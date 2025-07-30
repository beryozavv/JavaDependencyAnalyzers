package com.beryozavv;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.JavaCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class EclipseDependencyAnalyzer {

    // Корневой путь проекта для анализа
    private final Path sourceRoot;

    // Список путей к библиотекам для разрешения зависимостей
    private final List<Path> classpath;

    // Результаты анализа: файл -> (строка -> список символов)
    private final Map<Path, Map<Integer, List<String>>> usageMap = new HashMap<>();

    /**
     * Создает анализатор зависимостей на основе Eclipse JDT Core
     *
     * @param sourceRoot корневой каталог с исходным кодом Java
     * @param classpath  список путей к JAR-файлам для разрешения зависимостей
     */
    public EclipseDependencyAnalyzer(Path sourceRoot, List<Path> classpath) {
        this.sourceRoot = sourceRoot;
        this.classpath = classpath;
    }

    /**
     * Запускает анализ всех Java-файлов в проекте
     *
     * @return карта зависимостей для каждого файла и строки
     * @throws IOException при ошибке доступа к файлам
     */
    public Map<Path, Map<Integer, List<String>>> analyze() throws IOException {
        // Проходим по всем Java-файлам в проекте
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    analyzeJavaFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return usageMap;
    }

    /**
     * Анализирует один Java-файл и собирает информацию о зависимостях
     *
     * @param javaFile путь к Java-файлу
     * @throws IOException при ошибке чтения файла
     */
    private void analyzeJavaFile(Path javaFile) throws IOException {
        // Читаем содержимое файла
        String source = Files.readString(javaFile, StandardCharsets.UTF_8);

        // Создаем и настраиваем парсер AST
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // Устанавливаем опции компилятора
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);

        // Устанавливаем classpath для разрешения зависимостей
        if (!classpath.isEmpty()) {
            String[] classpathEntries = classpath.stream()
                    .map(Path::toString)
                    .toArray(String[]::new);
            parser.setEnvironment(classpathEntries, new String[]{sourceRoot.toString()}, null, true);
        }
        parser.setUnitName("Example.java");

        // Получаем единицу компиляции (CompilationUnit)
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        // Создаем карту для хранения зависимостей по строкам в этом файле
        Map<Integer, List<String>> lineDepMap = new HashMap<>();

        // Запускаем посетителя для обхода AST
        cu.accept(new ASTVisitor() {

            // Посещаем выражения с вызовом методов
            @Override
            public boolean visit(MethodInvocation node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем выражения создания объектов
            @Override
            public boolean visit(ClassInstanceCreation node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем выражения с именами (переменные, поля и т.д.)
            @Override
            public boolean visit(SimpleName node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем простые типы (в объявлениях переменных, параметрах и т.д.)
            @Override
            public boolean visit(SimpleType node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем ссылки на типы (Type References)
            @Override
            public boolean visit(TypeLiteral node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем выражения доступа к полям
            @Override
            public boolean visit(FieldAccess node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем выражения с доступом к статическим членам класса
            @Override
            public boolean visit(QualifiedName node) {
                handleNode(node, lineDepMap);
                return true;
            }

            // Посещаем выражения с оператором instanceof
            @Override
            public boolean visit(InstanceofExpression node) {
                handleNode(node, lineDepMap);
                return true;
            }
        });

        // Если в файле найдены зависимости, добавляем их в общую карту
        if (!lineDepMap.isEmpty()) {
            usageMap.put(javaFile, lineDepMap);
        }
    }

    /**
     * Обрабатывает узел AST и извлекает информацию о зависимостях
     *
     * @param node       узел AST для анализа
     * @param lineDepMap карта для сохранения зависимостей по строкам
     */
    private void handleNode(ASTNode node, Map<Integer, List<String>> lineDepMap) {
        int lineNumber = getLineNumber(node);
        if (lineNumber == -1) return;

        // Список для хранения FQN (полных имен классов) в этом узле
        List<String> dependencies = new ArrayList<>();

        // Определяем тип узла и извлекаем соответствующую информацию
        try {
            if (node instanceof MethodInvocation) {
                extractMethodInvocationDependencies((MethodInvocation) node, dependencies);
            } else if (node instanceof ClassInstanceCreation) {
                extractClassInstanceCreationDependencies((ClassInstanceCreation) node, dependencies);
            } else if (node instanceof SimpleName) {
                extractSimpleNameDependencies((SimpleName) node, dependencies);
            } else if (node instanceof SimpleType) {
                extractSimpleTypeDependencies((SimpleType) node, dependencies);
            } else if (node instanceof TypeLiteral) {
                extractTypeLiteralDependencies((TypeLiteral) node, dependencies);
            } else if (node instanceof FieldAccess) {
                extractFieldAccessDependencies((FieldAccess) node, dependencies);
            } else if (node instanceof QualifiedName) {
                extractQualifiedNameDependencies((QualifiedName) node, dependencies);
            } else if (node instanceof InstanceofExpression) {
                extractInstanceofExpressionDependencies((InstanceofExpression) node, dependencies);
            }
        } catch (Exception e) {
            // Игнорируем ошибки при попытке получить информацию о зависимостях
            System.err.println("Ошибка при обработке узла " + node + " в строке " + lineNumber + ": " + e.getMessage());
        }

        // Если найдены зависимости, добавляем их в карту
        if (!dependencies.isEmpty()) {
            lineDepMap.computeIfAbsent(lineNumber, k -> new ArrayList<>())
                    .addAll(dependencies);
        }
    }

    // Методы для извлечения зависимостей из различных типов узлов AST

    private void extractMethodInvocationDependencies(MethodInvocation node, List<String> dependencies) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding != null) {
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }

            // Добавляем типы параметров
            for (ITypeBinding paramType : methodBinding.getParameterTypes()) {
                dependencies.add(paramType.getQualifiedName());
            }

            // Добавляем тип возвращаемого значения
            ITypeBinding returnType = methodBinding.getReturnType();
            if (returnType != null && !returnType.isPrimitive()) {
                dependencies.add(returnType.getQualifiedName());
            }
        }
    }

    private void extractClassInstanceCreationDependencies(ClassInstanceCreation node, List<String> dependencies) {
        ITypeBinding typeBinding = node.resolveTypeBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }

    private void extractSimpleNameDependencies(SimpleName node, List<String> dependencies) {
        IBinding binding = node.resolveBinding();
        if (binding == null) return;

        if (binding instanceof ITypeBinding) {
            // Ссылка на тип (класс, интерфейс, enum)
            ITypeBinding typeBinding = (ITypeBinding) binding;
            dependencies.add(typeBinding.getQualifiedName());
        } else if (binding instanceof IVariableBinding) {
            // Ссылка на переменную или поле
            IVariableBinding varBinding = (IVariableBinding) binding;
            ITypeBinding typeBinding = varBinding.getType();
            if (typeBinding != null && !typeBinding.isPrimitive()) {
                dependencies.add(typeBinding.getQualifiedName());
            }
        } else if (binding instanceof IMethodBinding) {
            // Ссылка на метод
            IMethodBinding methodBinding = (IMethodBinding) binding;
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }

    private void extractSimpleTypeDependencies(SimpleType node, List<String> dependencies) {
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }

    private void extractTypeLiteralDependencies(TypeLiteral node, List<String> dependencies) {
        ITypeBinding typeBinding = node.resolveTypeBinding();
        if (typeBinding != null) {
            // Получаем тип, на который ссылается выражение Class<T>
            ITypeBinding referencedType = typeBinding.getTypeArguments().length > 0 ?
                    typeBinding.getTypeArguments()[0] : null;
            if (referencedType != null) {
                dependencies.add(referencedType.getQualifiedName());
            } else {
                dependencies.add(typeBinding.getQualifiedName());
            }
        }
    }

    private void extractFieldAccessDependencies(FieldAccess node, List<String> dependencies) {
        IVariableBinding fieldBinding = node.resolveFieldBinding();
        if (fieldBinding != null) {
            // Тип поля
            ITypeBinding fieldType = fieldBinding.getType();
            if (fieldType != null && !fieldType.isPrimitive()) {
                dependencies.add(fieldType.getQualifiedName());
            }

            // Тип класса, в котором объявлено поле
            ITypeBinding declaringClass = fieldBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }

    private void extractQualifiedNameDependencies(QualifiedName node, List<String> dependencies) {
        IBinding binding = node.resolveBinding();
        if (binding == null) return;

        if (binding instanceof ITypeBinding) {
            // Полное имя типа
            ITypeBinding typeBinding = (ITypeBinding) binding;
            dependencies.add(typeBinding.getQualifiedName());
        } else if (binding instanceof IVariableBinding) {
            // Ссылка на поле
            IVariableBinding varBinding = (IVariableBinding) binding;

            // Тип поля
            ITypeBinding fieldType = varBinding.getType();
            if (fieldType != null && !fieldType.isPrimitive()) {
                dependencies.add(fieldType.getQualifiedName());
            }

            // Тип класса, в котором объявлено поле
            ITypeBinding declaringClass = varBinding.getDeclaringClass();
            if (declaringClass != null) {
                dependencies.add(declaringClass.getQualifiedName());
            }
        }
    }

    private void extractInstanceofExpressionDependencies(InstanceofExpression node, List<String> dependencies) {
        ITypeBinding typeBinding = node.getRightOperand().resolveBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }

    /**
     * Получает номер строки для узла AST
     *
     * @param node узел AST
     * @return номер строки или -1, если информация недоступна
     */
    private int getLineNumber(ASTNode node) {
        CompilationUnit cu = (CompilationUnit) node.getRoot();
        return cu.getLineNumber(node.getStartPosition());
    }

    /**
     * Основной метод для запуска анализатора
     *
     * @param args аргументы командной строки: путь к проекту и пути к JAR-файлам
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar dependency-analyzer.jar <source-root> [<jar1> <jar2> ...]\n" +
                    "  <source-root> - path to Java source code\n" +
                    "  <jar1> <jar2> ... - optional paths to dependency JARs");
            System.exit(1);
        }

        try {
            Path sourceRoot = Path.of(args[0]);

            // Собираем пути к JAR-файлам из аргументов
            List<Path> classpath = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                classpath.add(Path.of(args[i]));
            }

            PathResult pathResult = MyGradleConnector.GetClassAndSourcePaths(sourceRoot);
            String first = pathResult.getSourcePath().getFirst();
            List<Path> pathList = pathResult.getClassPath().stream()
                    .map(Paths::get)
                    .collect(Collectors.toList());

            // Создаем и запускаем анализатор
            EclipseDependencyAnalyzer analyzer = new EclipseDependencyAnalyzer(Path.of(first), pathList);
            Map<Path, Map<Integer, List<String>>> results = analyzer.analyze();

            // Выводим результаты анализа
            results.forEach((file, deps) -> {
                System.out.println("File: " + file);
                deps.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> {
                            System.out.printf("  Line %d -> %s%n",
                                    e.getKey(), String.join(", ", e.getValue()));
                        });
            });

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
