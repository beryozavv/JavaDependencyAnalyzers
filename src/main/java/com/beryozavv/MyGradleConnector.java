package com.beryozavv;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class MyGradleConnector {

    public static PathResult GetClassAndSourcePaths(Path projectPath) {
        File projectDir = new File(projectPath.toString());

        List<String> sourcepath = new ArrayList<>();
        List<String> classpath = new ArrayList<>();

        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .connect()) {

            IdeaProject project = connection.getModel(IdeaProject.class);

            for (IdeaModule module : project.getModules()) {
                for (IdeaContentRoot root : module.getContentRoots()) {
                    for (var src : root.getSourceDirectories()) {
                        sourcepath.add(src.getDirectory().getAbsolutePath());
                    }
                }

                for (IdeaDependency dep : module.getDependencies()) {
                    if (dep instanceof IdeaSingleEntryLibraryDependency) {
                        // одиночная библиотека
                        File bin = ((IdeaSingleEntryLibraryDependency) dep).getFile();
                        if (bin != null && bin.exists()) {
                            classpath.add(bin.getAbsolutePath());
                        }

                    } else if (dep instanceof IdeaModuleDependency) {
                        // зависимость на другой модуль
                        String targetName = ((IdeaModuleDependency) dep).getTargetModuleName();
                        // находим сам модуль по имени
                        var targetModule = project.getModules()
                                .stream()
                                .filter(m -> m.getName().equals(targetName))
                                .findFirst();
                        if (targetModule.isPresent()) {
                            IdeaCompilerOutput out = targetModule.get().getCompilerOutput();
                            if (out != null) {
                                File outDir = out.getOutputDir();
                                if (outDir.exists()) {
                                    classpath.add(outDir.getAbsolutePath());
                                }
                            }
                        }

                    } else {
                        // на будущее: если появятся другие типы
                        System.err.println("Неизвестный тип зависимости: "
                                + dep.getClass().getSimpleName()
                                + ", scope=" + ((IdeaDependency) dep).getScope());
                    }
                }
            }

            return new PathResult(sourcepath, classpath);
        }
    }
}
