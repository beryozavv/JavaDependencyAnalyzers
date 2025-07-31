package com.beryozavv.dependencyExtractorStrategies;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeLiteral;

import java.util.List;

/**
 * Стратегия для извлечения зависимостей из литералов типов
 */
public class TypeLiteralDependencyExtractor implements DependencyExtractorStrategy<TypeLiteral> {

    @Override
    public void extractDependencies(TypeLiteral node, List<String> dependencies) {
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
}
