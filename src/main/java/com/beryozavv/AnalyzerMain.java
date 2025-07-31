package com.beryozavv;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;


public class AnalyzerMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("""
                    Usage: java -jar dependency-analyzer.jar <source-root>
                      <source-root> - path to Java source code""");
            System.exit(1);
        }

        try {
            Path sourceRoot = Path.of(args[0]);

            PathResult pathResult = GradleConnectorWrapper.GetClassAndSourcePaths(sourceRoot);
            String first = pathResult.getSourcePath().getFirst();

            JavaDependencyAnalyzer analyzer = new JavaDependencyAnalyzer(Path.of(first), pathResult.getClassPath());
            Map<Path, Map<Integer, Set<String>>> results = analyzer.analyze();

            results.forEach((file, deps) -> {
                System.out.println("File: " + file);
                deps.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e ->
                                System.out.printf("  Line %d -> %s%n", e.getKey(), String.join(", ", e.getValue()))
                        );
            });

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
