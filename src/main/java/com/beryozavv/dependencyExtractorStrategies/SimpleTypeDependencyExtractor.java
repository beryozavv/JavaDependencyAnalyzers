package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleType;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из простых типов
 */
public class SimpleTypeDependencyExtractor implements DependencyExtractorStrategy<SimpleType> {

    @Override
    public void extractDependencies(SimpleType node, List<String> dependencies) {
        ITypeBinding typeBinding = node.resolveBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }
}
