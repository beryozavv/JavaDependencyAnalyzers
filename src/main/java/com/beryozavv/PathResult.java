package com.beryozavv;

import java.util.List;

public class PathResult {
    private final List<String> sourcePath;
    private final List<String> classPath;

    public PathResult(List<String> sourcePath, List<String> classPath) {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
    }

    public List<String> getSourcePath() {
        return sourcePath;
    }

    public List<String> getClassPath() {
        return classPath;
    }
}