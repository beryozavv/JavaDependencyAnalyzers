package com.beryozavv;

import com.beryozavv.dependencyExtractorStrategies.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.JavaCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Анализатор зависимостей в Java-файлах
 */
public class JavaDependencyAnalyzer {

    // Корневой путь проекта для анализа
    private final Path sourceRoot;

    // Список путей к библиотекам для разрешения зависимостей
    private final List<String> classpath;

    // Результаты анализа: файл -> (строка -> список символов)
    private final Map<Path, Map<Integer, Set<String>>> usageMap = new HashMap<>();

    // Стратегии для извлечения зависимостей
    private final Map<Class<? extends ASTNode>, DependencyExtractorStrategy<? extends ASTNode>> extractorStrategies = new HashMap<>();

    /**
     * Создает анализатор зависимостей на основе Eclipse JDT Core
     *
     * @param sourceRoot корневой каталог с исходным кодом Java
     * @param classpath  список путей к JAR-файлам для разрешения зависимостей
     */
    public JavaDependencyAnalyzer(Path sourceRoot, List<String> classpath) {
        this.sourceRoot = sourceRoot;
        this.classpath = classpath;

        // Инициализация стратегий
        initializeExtractorStrategies();
    }

    /**
     * Запускает анализ всех Java-файлов в проекте
     *
     * @return карта зависимостей для каждого файла и строки
     * @throws IOException при ошибке доступа к файлам
     */
    public Map<Path, Map<Integer, Set<String>>> analyze() throws IOException {
        // Проходим по всем Java-файлам в проекте
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
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
     * Инициализирует стратегии для извлечения зависимостей
     */
    private void initializeExtractorStrategies() {
        extractorStrategies.put(MethodInvocation.class, new MethodInvocationDependencyExtractor());
        extractorStrategies.put(ClassInstanceCreation.class, new ClassInstanceCreationDependencyExtractor());
        extractorStrategies.put(SimpleName.class, new SimpleNameDependencyExtractor());
        extractorStrategies.put(SimpleType.class, new SimpleTypeDependencyExtractor());
        extractorStrategies.put(TypeLiteral.class, new TypeLiteralDependencyExtractor());
        extractorStrategies.put(FieldAccess.class, new FieldAccessDependencyExtractor());
        extractorStrategies.put(QualifiedName.class, new QualifiedNameDependencyExtractor());
        extractorStrategies.put(InstanceofExpression.class, new InstanceofExpressionDependencyExtractor());
    }

    /**
     * Анализирует один Java-файл и собирает информацию о зависимостях
     *
     * @param javaFile путь к Java-файлу
     * @throws IOException при ошибке чтения файла
     */
    private void analyzeJavaFile(Path javaFile) throws IOException {
        CompilationUnit cu = parseJavaFile(javaFile);

        // Создаем карту для хранения зависимостей по строкам в этом файле
        Map<Integer, Set<String>> lineDepMap = new HashMap<>();

        runVisitor(cu, lineDepMap);

        // Если в файле найдены зависимости, добавляем их в общую карту
        if (!lineDepMap.isEmpty()) {
            usageMap.put(javaFile, lineDepMap);
        }
    }

    /**
     * Создает ASTVisitor и подписывается на события
     *
     * @param cu
     * @param lineDepMap
     */
    private void runVisitor(CompilationUnit cu, Map<Integer, Set<String>> lineDepMap) {
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
    }

    /**
     * Создает ASTParser, конфигурирует его и получает (CompilationUnit) ASTNode
     *
     * @param javaFile
     * @return
     * @throws IOException
     */
    private CompilationUnit parseJavaFile(Path javaFile) throws IOException {
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
            String[] classpathEntries = classpath.toArray(String[]::new);
            parser.setEnvironment(classpathEntries, new String[]{sourceRoot.toString()}, null, true);
        }
        parser.setUnitName("Example.java");

        // Получаем единицу компиляции (CompilationUnit)
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        return cu;
    }

    /**
     * Обрабатывает узел AST и извлекает информацию о зависимостях
     *
     * @param node       узел AST для анализа
     * @param lineDepMap карта для сохранения зависимостей по строкам
     */
    private void handleNode(ASTNode node, Map<Integer, Set<String>> lineDepMap) {
        int lineNumber = getLineNumber(node);
        if (lineNumber == -1) return;

        // Список для хранения FQN (полных имен классов) в этом узле
        List<String> dependencies = new ArrayList<>();

        // Определяем тип узла и применяем соответствующую стратегию
        try {
            applyExtractorStrategy(node, dependencies);
        } catch (Exception e) {
            // Игнорируем ошибки при попытке получить информацию о зависимостях
            System.err.println("Ошибка при обработке узла " + node + " в строке " + lineNumber + ": " + e.getMessage());
        }

        // Если найдены зависимости, добавляем их в карту
        if (!dependencies.isEmpty()) {
            lineDepMap.computeIfAbsent(lineNumber, k -> new HashSet<>())
                    .addAll(dependencies);
        }
    }

    /**
     * Применяет соответствующую стратегию для извлечения зависимостей из узла AST
     *
     * @param node         узел AST для анализа
     * @param dependencies список для сохранения найденных зависимостей
     */
    @SuppressWarnings("unchecked")
    private <T extends ASTNode> void applyExtractorStrategy(T node, List<String> dependencies) {
        Class<? extends ASTNode> nodeClass = node.getClass();
        DependencyExtractorStrategy<T> strategy = (DependencyExtractorStrategy<T>) extractorStrategies.get(nodeClass);
        strategy.extractDependencies(node, dependencies);
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

}
