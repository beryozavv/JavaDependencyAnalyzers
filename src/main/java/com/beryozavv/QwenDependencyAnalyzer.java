package com.beryozavv;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class QwenDependencyAnalyzer {

    private final CombinedTypeSolver typeSolver;
    private final JavaSymbolSolver symbolSolver;
    private final Path projectRootPath;
    private final Set<Path> dependencyJarPaths; // Пути к JAR-файлам зависимостей

    public QwenDependencyAnalyzer(Path projectRootPath, Set<Path> dependencyJarPaths) {
        this.projectRootPath = projectRootPath;
        this.dependencyJarPaths = dependencyJarPaths;
        this.typeSolver = new CombinedTypeSolver();

        // Добавляем TypeSolver для исходников проекта
        typeSolver.add(new JavaParserTypeSolver(projectRootPath));

        // Добавляем TypeSolver для JAR-файлов зависимостей
        // JarTypeSolver может сообщить, из какого JAR-файла был загружен тип
        for (Path jarPath : dependencyJarPaths) {
            try {
                typeSolver.add(new JarTypeSolver(jarPath));
                System.out.println("Добавлен JAR: " + jarPath);
            } catch (IOException e) {
                System.err.println("Ошибка при добавлении JAR: " + jarPath + ". Причина: " + e.getMessage());
            }
        }

        // ReflectionTypeSolver как резервный вариант (может быть неточным)
        // typeSolver.add(new ReflectionTypeSolver());

        this.symbolSolver = new JavaSymbolSolver(typeSolver);
    }


    public void analyze() {
        System.out.println("Начинаем анализ проекта: " + projectRootPath);

        try {
            // Проходим по всем .java файлам в проекте
            Files.walkFileTree(projectRootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        System.out.println("\n--- Анализ файла: " + projectRootPath.relativize(file) + " ---");
                        analyzeJavaFile(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Ошибка при обходе файлов проекта: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void analyzeJavaFile(Path javaFilePath) {
        try {
            // Парсим файл
            ParseResult<CompilationUnit> parseResult = new JavaParser()
                    //.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                    .parse(javaFilePath);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                // Устанавливаем Symbol Solver для CompilationUnit
                cu.setData(Node.SYMBOL_RESOLVER_KEY, symbolSolver);

                // Проходим по всем узлам в файле
                cu.accept(new VoidVisitorAdapter<Void>() {

                    // Обработка вызовов методов
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        try {
                            ResolvedMethodDeclaration resolvedMethod = n.resolve();
                            String fqn = resolvedMethod.getQualifiedName();
                            // Извлекаем FQN класса, в котором объявлен метод
                            String classFqn = resolvedMethod.declaringType().getQualifiedName();
                            processDependency(n, classFqn, "Method Call: " + fqn);
                        } catch (UnsolvedSymbolException e) {
                            System.out.println("Не удалось разрешить символ в строке " + n.getBegin().map(p -> p.line).orElse(-1) + ": " + n);
                        } catch (Exception e) {
                            // Игнорируем другие ошибки разрешения
                        }
                    }

                    // Обработка создания объектов (new)
                    @Override
                    public void visit(ObjectCreationExpr n, Void arg) {
                        super.visit(n, arg);
                        try {
                            ResolvedType resolvedType = n.calculateResolvedType();
                            if (resolvedType instanceof ResolvedReferenceType) {
                                String fqn = ((ResolvedReferenceType) resolvedType).getQualifiedName();
                                processDependency(n, fqn, "Object Creation: new " + fqn);
                            }
                        } catch (UnsolvedSymbolException e) {
                            System.out.println("Не удалось разрешить символ в строке " + n.getBegin().map(p -> p.line).orElse(-1) + ": " + n);
                        } catch (Exception e) {
                            // Игнорируем другие ошибки разрешения
                        }
                    }

                    // Обработка обращений к статическим полям/методам через имя класса (например, System.out)
                    @Override
                    public void visit(NameExpr n, Void arg) {
                        super.visit(n, arg);
                        // Пытаемся разрешить как значение/поле
                        try {
                            ResolvedValueDeclaration resolvedValue = n.resolve();
                            String typeName = resolvedValue.getType().describe();
                            // Часто typeName уже содержит FQN, но для гарантии пытаемся найти декларацию типа
                            if (resolvedValue instanceof ResolvedDeclaration) {
                                Optional<ResolvedReferenceTypeDeclaration> container = ((ResolvedDeclaration) resolvedValue).asType().containerType();
                                if (container.isPresent()) {
                                    String containerFqn = container.get().getQualifiedName();
                                    processDependency(n, containerFqn, "Static Access (NameExpr): " + n.getName() + " from " + containerFqn);
                                } else {
                                    // Если контейнер не найден, используем typeName, если это выглядит как FQN
                                    if (typeName.contains(".")) {
                                        processDependency(n, typeName, "Static Access (NameExpr - fallback): " + n.getName() + " type " + typeName);
                                    }
                                }
                            }
                        } catch (UnsolvedSymbolException e) {
                            // Это нормально для простых имен, которые не являются статическими ссылками
                        } catch (Exception e) {
                            // Игнорируем другие ошибки разрешения
                        }
                    }

                    // Обработка явных упоминаний типов (например, в extends, implements, переменных)
                    @Override
                    public void visit(ClassOrInterfaceType n, Void arg) {
                        super.visit(n, arg);
                        try {
                            ResolvedReferenceType resolvedType = (ResolvedReferenceType) n.resolve();
                            String fqn = resolvedType.getQualifiedName();
                            processDependency(n, fqn, "Type Reference: " + fqn);
                        } catch (UnsolvedSymbolException e) {
                            System.out.println("Не удалось разрешить тип в строке " + n.getBegin().map(p -> p.line).orElse(-1) + ": " + n);
                        } catch (Exception e) {
                            // Игнорируем другие ошибки разрешения
                        }
                    }

                    // Обработка импортов (частично, для понимания контекста)
                    @Override
                    public void visit(ImportDeclaration n, Void arg) {
                        super.visit(n, arg);
                        // Импорты сами по себе не анализируем как "зависимости в строке", но они важны для разрешения символов.
                        // SymbolSolver использует их автоматически.
                        System.out.println("  [Импорт] Строка " + n.getBegin().map(p -> p.line).orElse(-1) + ": " + n.getName());
                    }


                }, null);

            } else {
                System.err.println("Не удалось распарсить файл: " + javaFilePath);
                parseResult.getProblems().forEach(problem -> System.err.println("  Проблема: " + problem));
            }
        } catch (IOException e) {
            System.err.println("Ошибка при чтении файла: " + javaFilePath);
            e.printStackTrace();
        }
    }

    // Основной метод для определения зависимости по FQN
    private void processDependency(Node node, String fqn, String context) {
        int lineNumber = node.getBegin().map(pos -> pos.line).orElse(-1);

        // Пропускаем стандартные Java классы (частично)
        if (fqn.startsWith("java.") || fqn.startsWith("javax.")) {
            // System.out.println("Пропущен стандартный класс: " + fqn + " в строке " + lineNumber);
            return;
        }

        // Пропускаем классы самого проекта (упрощенная проверка)
        // Более точная проверка потребовала бы анализа package и структуры исходников
        try {
            // Пытаемся получить декларацию типа
            // Если она найдена JavaParserTypeSolver'ом (для исходников проекта), то это, скорее всего, внутренний тип
            // Этот способ не идеален, но может помочь отфильтровать многие внутренние типы
            // Более надежный способ - проверить, находится ли FQN в package'ах проекта
            String packageName = fqn.substring(0, Math.max(0, fqn.lastIndexOf('.')));
            // Проверка пути к исходникам проекта - упрощенная
            // Path expectedSourcePath = projectRootPath.resolve(packageName.replace('.', File.separatorChar));
            // Это сложно сделать точно без знания структуры модулей

            // Альтернатива: используем TypeSolver напрямую
            SymbolReference<ResolvedReferenceTypeDeclaration> declaration = typeSolver.tryToSolveType(fqn);
            if (declaration.isSolved()) {
                // Проверим, откуда решен тип
                // JarTypeSolver может предоставить информацию о JAR-файле
                // Однако прямого API для получения JAR-файла из ResolvedDeclaration нет
                // Мы можем попробовать обернуть TypeSolver или использовать внутренние API (не рекомендуется)

                // Более простой подход: если тип решен JavaParserTypeSolver'ом, то он из исходников
                // Это требует проверки типа конкретного TypeSolver'а, что хрупко
                // Лучше всего полагаться на то, что мы знаем classpath и можем сопоставлять FQN с ним

            }

        } catch (Exception e) {
            // Игнорируем ошибки при проверке
        }


//        // Пытаемся найти JAR-файл, из которого взят этот тип
//        String jarPath = findJarForType(fqn);

//        if (jarPath != null) {
//            System.out.println("  [Внешняя зависимость] Строка " + lineNumber + " (" + context + "): " + fqn);
//            System.out.println("    -> Найден в JAR: " + jarPath);
//        } else {
            // Тип не найден в JAR-ах. Возможно, это тип из другого модуля проекта или ошибка разрешения.
            // Мы можем попробовать определить, принадлежит ли он пакетам проекта
            boolean isProjectType = isLikelyProjectType(fqn);
            if (isProjectType) {
                System.out.println("  [Внутренний тип проекта] Строка " + lineNumber + " (" + context + "): " + fqn);
            } else {
                System.out.println("  [Неопределенная зависимость] Строка " + lineNumber + " (" + context + "): " + fqn + " (JAR не найден, возможно внутренний тип или ошибка)");
            }
        //}
    }

    // Простая проверка, может ли тип принадлежать проекту
    private boolean isLikelyProjectType(String fqn) {
        // Это очень упрощенная проверка.
        // В реальности нужно знать groupId/artifactId проекта и его структуру пакетов.
        // Например, если проект называется com.mycompany.myproject, то типы из com.mycompany.myproject.* скорее всего внутренние.
        // Здесь просто проверим, не начинается ли FQN с какого-то известного стороннего префикса.
        List<String> commonExternalPrefixes = Arrays.asList(
                "org.springframework", "com.fasterxml.jackson", "org.apache", "org.hibernate",
                "org.slf4j", "ch.qos.logback", "jakarta.", "org.junit", "org.mockito",
                "com.google.", "org.apache.commons"
        );

        for (String prefix : commonExternalPrefixes) {
            if (fqn.startsWith(prefix)) {
                return false; // Начинается с внешнего префикса -> внешний тип
            }
        }
        // Если не нашли внешних префиксов, предполагаем, что это тип проекта
        // Это не гарантирует точности!
        return true;
    }


    // Пытаемся найти JAR-файл для данного FQN
    // Этот метод использует не самый надежный способ, но показывает концепцию
//    private String findJarForType(String fqn) {
//        try {
//            // Пробуем решить тип и получить информацию о его местоположении
//            // Прямого API в JavaSymbolSolver для этого нет
//            // Можно попробовать использовать Reflection или анализировать внутреннее состояние TypeSolver'ов
//            // Это хакерский и ненадежный способ, не рекомендуется для продакшена
//
//            // Альтернатива: если мы добавляли JarTypeSolver, мы можем попробовать использовать его напрямую
//            // Но это требует итерации по ним и вызова защищенных методов, что нехорошо.
//
//            // Более надежный способ: заранее создать карту FQN -> JAR Path
//            // Например, при добавлении JarTypeSolver можно было бы запомнить эту информацию.
//            // Или использовать отдельную библиотеку для анализа JAR-файлов и построения такой карты.
//
//            // Для демонстрации используем упрощенный подход:
//            // Пробуем загрузить класс и получить ProtectionDomain
//            // Это работает только если класс уже загружен или может быть загружен текущим ClassLoader'ом
//            // и требует, чтобы зависимости были в classpath анализатора, что не всегда удобно.
//
//            /*
//            Class<?> clazz = Class.forName(fqn);
//            ProtectionDomain pd = clazz.getProtectionDomain();
//            CodeSource cs = pd.getCodeSource();
//            if (cs != null) {
//                URL location = cs.getLocation();
//                if ("file".equals(location.getProtocol()) && location.getPath().endsWith(".jar")) {
//                    return new File(location.getPath()).getAbsolutePath();
//                }
//            }
//            */
//
//            // Возвращаем null, если не можем определить
//            // В реальной реализации здесь должна быть логика поиска по известным JAR-ам
//            // или по карте FQN -> JAR, построенной заранее.
//
//            // Попробуем обойти добавленные JarTypeSolver-ы
//            // Это использует внутреннее API и может сломаться в будущих версиях
//            for (com.github.javaparser.symbolsolver.model.resolution.TypeSolver ts : getAllTypeSolvers(typeSolver)) {
//                if (ts instanceof JarTypeSolver) {
//                    JarTypeSolver jts = (JarTypeSolver) ts;
//                    // Проверим, содержит ли этот JAR указанный тип
//                    // Нет прямого публичного метода, но можно попробовать решить
//                    try {
//                        Optional<com.github.javaparser.symbolsolver.model.declarations.ReferenceTypeDeclaration> rtd = jts.tryToSolveType(fqn);
//                        if (rtd.isPresent()) {
//                            // Нашли! Попробуем получить путь к JAR.
//                            // JarTypeSolver хранит путь в приватном поле `jarAbsolutePath`
//                            // Используем Reflection (НЕ рекомендуется!)
//                            java.lang.reflect.Field field = JarTypeSolver.class.getDeclaredField("jarAbsolutePath");
//                            field.setAccessible(true);
//                            Path jarPath = (Path) field.get(jts);
//                            return jarPath.toString();
//                        }
//                    } catch (Exception e) {
//                        // Игнорируем ошибки рефлексии
//                    }
//                }
//            }
//
//
//        } catch (/*ClassNotFoundException |*/ Exception e) {
//            // Класс не найден или ошибка рефлексии
//        }
//        return null; // Не найден JAR
//    }

    // Вспомогательный метод для получения всех TypeSolver'ов из CombinedTypeSolver
//    private List<com.github.javaparser.symbolsolver.model.resolution.TypeSolver> getAllTypeSolvers(CombinedTypeSolver combinedSolver) {
//        List<com.github.javaparser.symbolsolver.model.resolution.TypeSolver> solvers = new ArrayList<>();
//        // CombinedTypeSolver хранит решатели в приватном списке `typeSolvers`
//        // Используем рефлексию (НЕ рекомендуется!)
//        try {
//            java.lang.reflect.Field field = CombinedTypeSolver.class.getDeclaredField("typeSolvers");
//            field.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            List<com.github.javaparser.symbolsolver.model.resolution.TypeSolver> internalSolvers =
//                    (List<com.github.javaparser.symbolsolver.model.resolution.TypeSolver>) field.get(combinedSolver);
//            solvers.addAll(internalSolvers);
//        } catch (Exception e) {
//            System.err.println("Не удалось получить внутренние TypeSolver'ы через рефлексию: " + e.getMessage());
//        }
//        return solvers;
//    }


    // --- Метод для запуска анализатора ---
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Использование: java DependencyAnalyzer <путь_к_проекту> <путь_к_jar1> [<путь_к_jar2> ...]");
            System.exit(1);
        }

        Path projectPath = Paths.get(args[0]);
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            System.err.println("Указанный путь к проекту не существует или не является директорией: " + projectPath);
            System.exit(1);
        }

        Set<Path> jarPaths = Arrays.stream(args).skip(1) // Пропускаем первый аргумент (путь к проекту)
                .map(Paths::get)
                .filter(Files::exists)
                .filter(p -> p.toString().endsWith(".jar") || Files.isDirectory(p)) // JAR или директория с классами
                .collect(Collectors.toSet());

        if (jarPaths.isEmpty()) {
            System.out.println("Предупреждение: Не указаны пути к JAR-файлам зависимостей. Анализ будет неполным.");
        }

        var analyzer = new QwenDependencyAnalyzer(projectPath, jarPaths);
        analyzer.analyze();
    }
}
