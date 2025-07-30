package com.beryozavv;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class TypeSolverUtil {

    /**
     * Добавляет пути из PathResult в CombinedTypeSolver
     *
     * @param typeSolver CombinedTypeSolver, в который будут добавлены пути
     * @param pathResult объект PathResult, содержащий пути к исходникам и библиотекам
     */
    public static void addPathsToTypeSolver(CombinedTypeSolver typeSolver, PathResult pathResult) {
        // Добавляем пути к исходникам проекта
        for (String sourcePath : pathResult.getSourcePath()) {
            typeSolver.add(new JavaParserTypeSolver(Paths.get(sourcePath)));
        }

        // Добавляем пути к зависимостям (JAR файлы)
        for (String classPath : pathResult.getClassPath()) {
            try {
                if (classPath.endsWith(".jar")) {
                    typeSolver.add(new JarTypeSolver(classPath));
                } else {
                    typeSolver.add(new JavaParserTypeSolver(Paths.get(classPath)));
                }
                //typeSolver.add(new JarTypeSolver(new File(classPath)));
            } catch (IOException e) {
                System.err.println("Не удалось добавить JAR файл в typeSolver: " + classPath);
                e.printStackTrace();
            }
        }
    }
}
