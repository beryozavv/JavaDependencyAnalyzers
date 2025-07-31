package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InstanceofExpression;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из выражений instanceof
 */
public class InstanceofExpressionDependencyExtractor implements DependencyExtractorStrategy<InstanceofExpression> {

    @Override
    public void extractDependencies(InstanceofExpression node, List<String> dependencies) {
        ITypeBinding typeBinding = node.getRightOperand().resolveBinding();
        if (typeBinding != null) {
            dependencies.add(typeBinding.getQualifiedName());
        }
    }
}
