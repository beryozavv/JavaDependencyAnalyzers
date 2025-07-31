package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ITypeBinding;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из выражений создания экземпляров классов
 */
public class ClassInstanceCreationDependencyExtractor implements DependencyExtractorStrategy<ClassInstanceCreation> {

    @Override
    public void extractDependencies(ClassInstanceCreation node, List<String> dependencies) {
        ITypeBinding typeBinding = node.resolveTypeBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }
}
