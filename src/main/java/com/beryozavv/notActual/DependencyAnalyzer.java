package com.beryozavv.notActual;

import com.beryozavv.GradleConnectorWrapper;
import com.beryozavv.PathResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
        //typeSolver.add(new JreTypeSolver());
        typeSolver.add(new ReflectionTypeSolver(true));
        //typeSolver.add(new JarTypeSolver());
        typeSolver.add(new ClassLoaderTypeSolver(Thread.currentThread().getContextClassLoader()));
        //typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        PathResult pathResult = GradleConnectorWrapper.GetClassAndSourcePaths(sourceRoot);
        TypeSolverUtil.addPathsToTypeSolver(typeSolver, pathResult);

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            javaFiles = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }

        Map<Path, Map<Integer, List<String>>> usageMap = new HashMap<>();

        for (Path file : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            Map<Integer, List<String>> lineDeps = new HashMap<>();
            getAccept(cu, symbolSolver, lineDeps);
            usageMap.put(file, lineDeps);
        }

        usageMap.forEach((file, deps) -> {
            System.out.println("File: " + file);
            deps.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        System.out.printf("  Line %d -> %s%n",
                                e.getKey(), String.join(", ", e.getValue()));
                    });
        });
//        usageMap.forEach((file, deps) -> {
//            System.out.println("File: " + file);
//            deps.entrySet().stream()
//                    .sorted(Map.Entry.comparingByKey()) // сортировка по line (ключу)
//                    .forEach(entry -> System.out.printf("  Line %d -> %s%n", entry.getKey(), entry.getValue()));
//        });
    }

    private static void getAccept(CompilationUnit cu, JavaSymbolSolver symbolSolver, Map<Integer, List<String>> lineDeps) {
        cu.accept(new VoidVisitorAdapter<>() {
            @Override
            public void visit(NameExpr n, Object arg) {
                handleNode(n);
                super.visit(n, arg);
            }

            @Override
            public void visit(MethodCallExpr n, Object arg) {
                handleNode(n);
                super.visit(n, arg);
            }

            @Override
            public void visit(ObjectCreationExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(ClassOrInterfaceType n, Object arg) {
                handleNode(n);
                super.visit(n, arg);
            }

            @Override
            public void visit(VariableDeclarator n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(FieldDeclaration n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(MethodDeclaration n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(ConstructorDeclaration n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(FieldAccessExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(ClassExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(CatchClause n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(CastExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(TypeExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            @Override
            public void visit(InstanceOfExpr n, Object arg) {
                handleNode(n); // тип создаваемого объекта
                super.visit(n, arg);
            }

            private void handleNode(Node n) {
                DependencyAnalyzer.handleNode(n, symbolSolver, lineDeps, cu);
            }
        }, null);
    }

    private static void handleNode(Node n, JavaSymbolSolver symbolSolver, Map<Integer, List<String>> lineDeps, CompilationUnit cu) {
        int line = n.getRange().map(r -> r.begin.line).orElse(-1);
        try {
            List<String> possibleImports = new ArrayList<>();
            if (n instanceof MethodCallExpr) {
                ResolvedMethodDeclaration m = symbolSolver
                        .resolveDeclaration(n, ResolvedMethodDeclaration.class);
                String declaringType = m.declaringType().getQualifiedName();
                possibleImports.add("static " + declaringType);
                // wildcard статический импорт всего класса
                //possibleImports.add("import static " + declaringType + ".*;");
            } else if (n instanceof FieldAccessExpr) {
                ResolvedValueDeclaration f = symbolSolver.resolveDeclaration(n, ResolvedValueDeclaration.class);
                if (f instanceof ResolvedFieldDeclaration) {
                    ResolvedFieldDeclaration fld = (ResolvedFieldDeclaration) f;
                    String declaringType = fld.declaringType().getQualifiedName();

                    possibleImports.add("static " + declaringType);
                    //possibleImports.add("import static " + declaringType + ".*;");
                }
            } else if (n instanceof ObjectCreationExpr objectCreationExpr) {
                ResolvedType resolvedType = objectCreationExpr.calculateResolvedType();
                String fqn = ((ResolvedReferenceType) resolvedType).getQualifiedName();
                possibleImports.add("static " + fqn);
            } else if (n instanceof NameExpr nameExpr) {
                var typeName = Resolve(nameExpr);
                possibleImports.add(typeName + ";");
            } else if (n instanceof ClassOrInterfaceType classOrInterfaceType) {
                ResolvedReferenceType resolvedType = (ResolvedReferenceType) classOrInterfaceType.resolve();
                String fqn = resolvedType.getQualifiedName();
                possibleImports.add(fqn + ";");

            } else if (n instanceof VariableDeclarator) {
                VariableDeclarator vd = (VariableDeclarator) n;
                ResolvedType rt = vd.getType().resolve();
                if (rt.isReferenceType()) {
                    String qn = rt.asReferenceType().getQualifiedName();
                    possibleImports.add(qn + ";");
                }
            } else if (n instanceof MethodDeclaration) {
                MethodDeclaration md = (MethodDeclaration) n;
                // return type
                ResolvedType ret = md.getType().resolve();
                if (ret.isReferenceType()) {
                    possibleImports.add(ret.asReferenceType().getQualifiedName() + ";");
                }
                // parameter types
                for (Parameter p : md.getParameters()) {
                    ResolvedType pt = p.getType().resolve();
                    if (pt.isReferenceType()) {
                        possibleImports.add(pt.asReferenceType().getQualifiedName() + ";");
                    }
                }

            } else if (n instanceof FieldDeclaration fieldDeclaration) {
                ResolvedFieldDeclaration resolve = fieldDeclaration.resolve();
                ResolvedType rt = resolve.getType();
                if (rt.isReferenceType()) {
                    possibleImports.add(rt.asReferenceType().getQualifiedName() + ";");
                }
            } else if (n instanceof ConstructorDeclaration) {
                ConstructorDeclaration cd = (ConstructorDeclaration) n;
                ResolvedConstructorDeclaration cons = symbolSolver
                        .resolveDeclaration(cd, ResolvedConstructorDeclaration.class);
                String declaringType = cons.declaringType().getQualifiedName();
                possibleImports.add(declaringType + ";");
                // parameter types
                for (Parameter p : cd.getParameters()) {
                    ResolvedType pt = p.getType().resolve();
                    if (pt.isReferenceType()) {
                        possibleImports.add(pt.asReferenceType().getQualifiedName() + ";");
                    }
                }
            } else if (n instanceof ClassExpr) {
                ClassExpr ce = (ClassExpr) n;
                ResolvedType rt = ce.getType().resolve();
                if (rt.isReferenceType()) {
                    possibleImports.add(rt.asReferenceType().getQualifiedName() + ";");
                }
            } else if (n instanceof CatchClause) {
                CatchClause cc = (CatchClause) n;
                var t = cc.getParameter().getType().asClassOrInterfaceType();
                ResolvedType rt = t.resolve();
                if (rt.isReferenceType()) {
                    possibleImports.add(rt.asReferenceType().getQualifiedName() + ";");
                }

            } else if (n instanceof TypeExpr) {
                TypeExpr te = (TypeExpr) n;
                ResolvedType rt = te.getType().resolve();
                if (rt.isReferenceType()) {
                    possibleImports.add(rt.asReferenceType().getQualifiedName() + ";");
                }
            } else if (n instanceof InstanceOfExpr) {
                InstanceOfExpr ioe = (InstanceOfExpr) n;
                ResolvedType rt = ioe.getType().resolve();
                if (rt.isReferenceType()) {
                    possibleImports.add(rt.asReferenceType().getQualifiedName() + ";");
                }
            }
//            // record unique imports
//            lineDeps.putIfAbsent(line, new ArrayList<>());
//            for (String imp : possibleImports) {
//                if (!lineDeps.get(line).contains(imp)) {
//                    lineDeps.get(line).add(imp);
//                }
//            }
            else {
                if (n instanceof PrimitiveType primitiveType) {
                    possibleImports.add(primitiveType.getType().name() + ";");
                } else if (n instanceof ArrayType arrayType) {
                    possibleImports.add(arrayType.getComponentType().toString() + ";");
                }
                // все остальные — пробуем тип
                ResolvedType rt = symbolSolver.calculateType((Expression) n);
                if (rt.isReferenceType()) {
                    String qName = rt.asReferenceType().getQualifiedName();
                    // точный импорт класса
                    possibleImports.add(qName + ";");
                    // wildcard‑импорт его пакета
                    int lastDot = qName.lastIndexOf('.');
                    if (lastDot > 0) {
                        String pkg = qName.substring(0, lastDot);
                        //possibleImports.add( pkg + ".*;");
                    }
                }
            }

            if (!possibleImports.isEmpty()) {
                // убираем дубликаты и сохраняем
                List<String> uniq = possibleImports.stream().distinct().collect(Collectors.toList());
                lineDeps.put(line, uniq);
            }
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            Optional<Path> path = cu.getStorage().map(storage -> storage.getPath());
            System.err.println("Error UnsolvedSymbolException in node " + n + "; In File " + path + " at line " + line + ": " + e.getMessage());
        } catch (Exception e) {
            Optional<Path> path = cu.getStorage().map(storage -> storage.getPath());
            System.err.println("Error Exception in node " + n + "; In File " + path + " at line " + line + ": " + e.getMessage());
        }
    }

    public static String Resolve(NameExpr n) {
        ResolvedDeclaration resolvedDeclaration = n.resolve();

        String fqn = null;
        String symbolKind = "Unknown";

        // Шаг 2: Определим тип разрешенного символа и извлечем FQN

        if (resolvedDeclaration instanceof ResolvedTypeDeclaration) {
            // Случай 1: NameExpr напрямую ссылается на тип (класс/интерфейс)
            // Например: MyClass.someStaticMethod() -> NameExpr 'MyClass'
            fqn = ((ResolvedTypeDeclaration) resolvedDeclaration).getQualifiedName();
            symbolKind = "Type";

        } else if (resolvedDeclaration instanceof ResolvedValueDeclaration) {
            // Случай 2: NameExpr ссылается на переменную, поле или параметр
            ResolvedValueDeclaration valueDecl = (ResolvedValueDeclaration) resolvedDeclaration;
            ResolvedType type = valueDecl.getType();

            if (type instanceof ResolvedReferenceType) {
                // Если тип переменной/поля - ссылочный (не примитив), получаем его FQN
                // Например: System.out.println() -> NameExpr 'System' разрешается в поле,
                // тип которого ResolvedReferenceType для 'java.lang.System'
                fqn = ((ResolvedReferenceType) type).getQualifiedName();
                symbolKind = "Value (Type: " + type.describe() + ")";
            } else {
                // Тип примитивный (int, boolean и т.д.), FQN не применим напрямую
                // Но мы можем получить FQN типа, в котором это поле/переменная объявлена, если нужно
                symbolKind = "Value (Primitive Type: " + type.describe() + ")";
                // Альтернативно, попробуем получить декларирующий тип:
                fqn = resolvedDeclaration.getName();
            }

        } else if (resolvedDeclaration instanceof ResolvedMethodDeclaration) {
            // Менее распространенный случай для NameExpr, но обработаем
            ResolvedMethodDeclaration methodDecl = (ResolvedMethodDeclaration) resolvedDeclaration;
            fqn = methodDecl.declaringType().getQualifiedName(); // FQN класса, где объявлен метод
            symbolKind = "Method (declared in " + fqn + ")";

        } else {
            // Неожиданный тип разрешенного символа
            symbolKind = resolvedDeclaration.getClass().getSimpleName();
            // Попробуем получить FQN, если есть подходящий метод
            if (resolvedDeclaration instanceof ResolvedReferenceTypeDeclaration) {
                fqn = ((ResolvedReferenceTypeDeclaration) resolvedDeclaration).getQualifiedName();
            }
        }
        return fqn;
    }
}