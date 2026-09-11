package com.gachisa.product.service;

import com.gachisa.category.entity.Category;
import com.gachisa.category.repository.CategoryRepository;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.product.dto.ProductCreateRequest;
import com.gachisa.product.dto.ProductPaymentInfo;
import com.gachisa.product.dto.ProductResponse;
import com.gachisa.product.dto.ProductUpdateRequest;
import com.gachisa.product.entity.Product;
import com.gachisa.product.entity.ProductStatus;
import com.gachisa.product.repository.ProductRepository;
import com.gachisa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request, String imageUrl) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        if (productRepository.existsBySellerIdAndName(sellerId, request.name())) {
            throw new CustomException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product product = Product.builder()
            .seller(userRepository.getReferenceById(sellerId))
            .category(category)
            .name(request.name())
            .description(request.description())
            .basePrice(request.basePrice())
            .stock(request.stock())
            .imageUrl(imageUrl)
            .status(ProductStatus.ON_SALE)
            .createdAt(LocalDateTime.now())
            .build();
        productRepository.save(product);

        return ProductResponse.of(product);
    }

    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
            .map(ProductResponse::of)
            .collect(Collectors.toList());
    }

    /** 판매자 본인이 등록한 상품만 조회 (상품 관리 페이지용) */
    public List<ProductResponse> getMyProducts(Long sellerId) {
        return productRepository.findBySellerId(sellerId).stream()
            .map(ProductResponse::of)
            .collect(Collectors.toList());
    }

    public ProductResponse getProduct(Long productId) {
        Product product = getProductOrThrow(productId);
        return ProductResponse.of(product);
    }

    public ProductPaymentInfo getPaymentInfo(Long productId) {
        Product product = getProductOrThrow(productId);
        return new ProductPaymentInfo(product.getId(), product.getBasePrice());
    }

    public List<ProductResponse> searchProducts(Long categoryId, Integer minPrice, Integer maxPrice, String keyword) {
        return productRepository.search(null, categoryId, minPrice, maxPrice, keyword).stream()
            .map(ProductResponse::of)
            .collect(Collectors.toList());
    }

    /** 판매자 본인 상품 안에서만 검색 (상품 관리 페이지용) - 판매중지 상품도 포함 */
    public List<ProductResponse> searchMyProducts(Long sellerId, Long categoryId, Integer minPrice,
                                                  Integer maxPrice, String keyword) {
        return productRepository.search(sellerId, categoryId, minPrice, maxPrice, keyword).stream()
            .map(ProductResponse::of)
            .collect(Collectors.toList());
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, Long sellerId, ProductUpdateRequest request) {
        Product product = getProductOrThrow(productId);
        validateOwner(product, sellerId);

        if (request.name() != null && !request.name().isBlank()) {
            if (!product.getName().equals(request.name())
                && productRepository.existsBySellerIdAndNameAndIdNot(sellerId, request.name(), productId)) {
                throw new CustomException(ErrorCode.PRODUCT_NAME_DUPLICATED);
            }
            product.updateName(request.name());
        }
        if (request.description() != null) {
            product.updateDescription(request.description());
        }
        if (request.basePrice() != null) {
            product.updateBasePrice(request.basePrice());
        }
        if (request.stock() != null) {
            product.updateStock(request.stock());
        }
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
            product.updateCategory(category);
        }

        return ProductResponse.of(product);
    }

    @Transactional
    public ProductResponse updateProductImage(Long productId, Long sellerId, String imageUrl) {
        Product product = getProductOrThrow(productId);
        validateOwner(product, sellerId);
        product.updateImageUrl(imageUrl);
        return ProductResponse.of(product);
    }

    @Transactional
    public void deleteProduct(Long productId, Long sellerId) {
        Product product = getProductOrThrow(productId);
        validateOwner(product, sellerId);
        product.stopSale();
    }

    @Transactional
    public ProductResponse resumeProduct(Long productId, Long sellerId) {
        Product product = getProductOrThrow(productId);
        validateOwner(product, sellerId);
        product.resumeSale();
        return ProductResponse.of(product);
    }

    private Product getProductOrThrow(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validateOwner(Product product, Long sellerId) {
        if (!product.isOwnedBy(sellerId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
