package com.beryozavv;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyAnalyzer {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -jar dependency-analyzer.jar <source-root> <libs-dir>");
            System.exit(1);
        }
        Path sourceRoot = Path.of(args[0]);
        Path libsDir = Path.of(args[1]);

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        try (Stream<Path> jars = Files.list(libsDir)) {
            jars.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> {
                        try {
                            typeSolver.add(new JarTypeSolver(jar.toAbsolutePath().toString()));
                        } catch (Exception e) {
                            System.err.println("Failed to add jar: " + jar + ", " + e.getMessage());
                        }
                    });
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            javaFiles = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }

        Map<Path, Map<Integer, String>> usageMap = new HashMap<>();

        for (Path file : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            Map<Integer, String> lineDeps = new HashMap<>();
            cu.accept(new VoidVisitorAdapter<>() {
                @Override
                public void visit(NameExpr n, Object arg) {
                    handleNode(n);
                    super.visit(n, arg);
                }
                @Override
                public void visit(MethodCallExpr m, Object arg) {
                    handleNode(m);
                    super.visit(m, arg);
                }
                private void handleNode(Node n) {
                    int line = n.getRange().map(r -> r.begin.line).orElse(-1);
                    try {
                        ResolvedDeclaration decl = symbolSolver.resolveDeclaration(n, ResolvedDeclaration.class);
                        String qualified;
                        if (decl instanceof ResolvedReferenceTypeDeclaration) {
                            qualified = ((ResolvedReferenceTypeDeclaration) decl).getQualifiedName();
                        } else if (decl instanceof ResolvedMethodDeclaration) {
                            qualified = ((ResolvedMethodDeclaration) decl).getQualifiedSignature();
                        } else if (decl instanceof ResolvedFieldDeclaration) {
                            qualified = ((ResolvedFieldDeclaration) decl).getType().describe();
                        } else {
                            qualified = decl.getName();
                        }
                        String dependency = findDependencyForType(qualified, libsDir);
                        if (dependency != null) {
                            lineDeps.put(line, dependency);
                        }
                    } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                        // игнорируем ошибки разрешения
                    } catch (Exception e) {
                        System.err.println("Error at line " + line + ": " + e.getMessage());
                    }
                }
            }, null);
            usageMap.put(file, lineDeps);
        }

        usageMap.forEach((file, deps) -> {
            System.out.println("File: " + file);
            deps.forEach((line, dep) -> System.out.printf("  Line %d -> %s%n", line, dep));
        });
    }

    private static String findDependencyForType(String qualifiedName, Path libsDir) {
        try (Stream<Path> jars = Files.list(libsDir)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).collect(Collectors.toList())) {
                try (var jf = new java.util.jar.JarFile(jar.toFile())) {
                    String entry = qualifiedName.replace('.', '/') + ".class";
                    if (jf.getEntry(entry) != null) {
                        return jar.getFileName().toString();
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }
}