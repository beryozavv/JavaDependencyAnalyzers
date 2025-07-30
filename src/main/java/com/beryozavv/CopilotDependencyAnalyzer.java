package com.beryozavv;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class CopilotDependencyAnalyzer {

    private final JavaParser javaParser;

    public CopilotDependencyAnalyzer(Path sourceRoot, List<Path> classpathJars) {
        // Настраиваем комбинированный решатель типов
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver());

        // Подключаем исходники проекта
        combinedSolver.add(new JavaParserTypeSolver(sourceRoot));

        // Добавляем внешние библиотеки (JAR-файлы)
        classpathJars.forEach(jar -> {
            try {
                combinedSolver.add(new JarTypeSolver(jar.toAbsolutePath().toString()));
            } catch (IOException e) {
                throw new RuntimeException("Не удалось подключить JAR: " + jar, e);
            }
        });

        // Конфигурируем парсер
        ParserConfiguration cfg = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        this.javaParser = new JavaParser(cfg);
    }

    /**
     * Запускает анализ и возвращает мапу:
     * ключ = путь к файлу, значение = мапа: номер строки → список зависимостей
     */
    public Map<Path, Map<Integer, Set<String>>> analyze(Path rootDir) throws IOException {
        Map<Path, Map<Integer, Set<String>>> result = new HashMap<>();

        // Рекурсивно обходим все .java-файлы
        try (Stream<Path> stream = Files.walk(rootDir)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaPath -> {
                        try {
                            analyzeFile(javaPath, result);
                        } catch (IOException e) {
                            System.err.println("Ошибка чтения " + javaPath + ": " + e.getMessage());
                        }
                    });
        }

        return result;
    }

    private void analyzeFile(Path path, Map<Path, Map<Integer, Set<String>>> result) throws IOException {
        Optional<CompilationUnit> cuOpt = javaParser.parse(path).getResult();
        if (cuOpt.isEmpty()) return;

        CompilationUnit cu = cuOpt.get();
        Map<Integer, Set<String>> perLine = new TreeMap<>();

        // Посещаем все выражения и объявления, собираем зависимости
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(NameExpr n, Void arg) {
                collectDependency(n, resolveSymbol(n), perLine);
                super.visit(n, arg);
            }

            @Override
            public void visit(MethodCallExpr mce, Void arg) {
                collectDependency(mce, resolveExecutable(mce), perLine);
                super.visit(mce, arg);
            }

            @Override
            public void visit(ObjectCreationExpr oce, Void arg) {
                collectDependency(oce, resolveType(oce), perLine);
                super.visit(oce, arg);
            }

            @Override
            public void visit(FieldAccessExpr fae, Void arg) {
                collectDependency(fae, resolveSymbol(fae), perLine);
                super.visit(fae, arg);
            }

            @Override
            public void visit(ImportDeclaration id, Void arg) {
                // можно учесть в начале файла
                super.visit(id, arg);
            }

            // При необходимости можно добавить еще visit-методы:
            // LambdaExpr, AnnotationExpr, VariableDeclarationExpr и т.д.
        }, null);

        if (!perLine.isEmpty()) {
            result.put(path, perLine);
        }
    }

    private Optional<String> resolveSymbol(FieldAccessExpr expr) {
        try {
            ResolvedValueDeclaration decl = expr.resolve();
            return Optional.of(decl.asType().getQualifiedName());
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private Optional<String> resolveSymbol(NameExpr expr) {
        try {
            ResolvedValueDeclaration decl = expr.resolve();
            return Optional.of(decl.asType().getQualifiedName());
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private Optional<String> resolveExecutable(MethodCallExpr mce) {
        try {
            ResolvedMethodDeclaration decl = mce.resolve();
            return Optional.of(decl.getQualifiedSignature());
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private Optional<String> resolveType(ObjectCreationExpr oce) {
        try {
            ResolvedType type = oce.calculateResolvedType();
            return Optional.of(type.describe());
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private void collectDependency(Node node, Optional<String> maybeDep, Map<Integer, Set<String>> perLine) {
        if (maybeDep.isEmpty() || !node.getRange().isPresent()) return;

        int line = node.getRange().get().begin.line;
        perLine.computeIfAbsent(line, l -> new HashSet<>()).add(maybeDep.get());
    }

    // Пример запуска
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java ... <source-root> <jar1> [<jar2> ...]");
            return;
        }

        Path srcRoot = Paths.get(args[0]);
        List<Path> jars = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            jars.add(Paths.get(args[i]));
        }

        CopilotDependencyAnalyzer analyzer = new CopilotDependencyAnalyzer(srcRoot, jars);
        Map<Path, Map<Integer, Set<String>>> report = analyzer.analyze(srcRoot);

        // Выводим результат
        report.forEach((file, map) -> {
            System.out.println("Файл: " + file);
            map.forEach((line, deps) ->
                    System.out.printf("  %3d : %s%n", line, deps)
            );
        });
    }
}
