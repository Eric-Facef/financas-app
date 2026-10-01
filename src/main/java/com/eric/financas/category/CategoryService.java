package com.eric.financas.category;

import com.eric.financas.audit.AuditAction;
import com.eric.financas.audit.AuditService;
import com.eric.financas.category.dto.CategoryRequest;
import com.eric.financas.category.dto.CategoryResponse;
import com.eric.financas.common.exception.BusinessException;
import com.eric.financas.common.exception.ConflictException;
import com.eric.financas.common.exception.NotFoundException;
import com.eric.financas.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, CategoryKind kind) {
        var result = kind == null
                ? categories.findByUserIdOrderByNameAsc(userId)
                : categories.findByUserIdAndKindOrderByNameAsc(userId, kind);
        return result.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByUserIdAndNameIgnoreCaseAndKind(userId, name, req.kind())) {
            throw new ConflictException("Já existe uma categoria com esse nome");
        }
        Category saved = categories.save(new Category(userId, name, req.kind(), req.icon(), req.color()));

        audit.record(userId, AuditAction.CATEGORIA_CRIADA, "Category", saved.getId().toString(), Map.of("nome", name));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryRequest req) {
        Category category = findOwned(userId, id);
        if (category.getKind() != req.kind()) {
            throw new BusinessException("Não é possível alterar o tipo (receita/despesa) de uma categoria");
        }
        String name = req.name().trim();
        if (!category.getName().equalsIgnoreCase(name)
                && categories.existsByUserIdAndNameIgnoreCaseAndKind(userId, name, req.kind())) {
            throw new ConflictException("Já existe uma categoria com esse nome");
        }
        category.setName(name);
        category.setIcon(req.icon());
        category.setColor(req.color());

        audit.record(userId, AuditAction.CATEGORIA_EDITADA, "Category", id.toString(), Map.of("nome", name));
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Category category = findOwned(userId, id);
        if (transactions.existsByCategoryId(id)) {
            throw new BusinessException("Categoria possui lançamentos e não pode ser excluída");
        }
        categories.delete(category);
        audit.record(userId, AuditAction.CATEGORIA_EXCLUIDA, "Category", id.toString(),
                Map.of("nome", category.getName()));
    }

    private Category findOwned(UUID userId, UUID id) {
        return categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
    }
}
