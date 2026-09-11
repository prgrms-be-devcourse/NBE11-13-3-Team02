package com.gachisa.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.category.entity.Category;
import com.gachisa.category.repository.CategoryRepository;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.product.dto.ProductCreateRequest;
import com.gachisa.product.dto.ProductResponse;
import com.gachisa.product.dto.ProductUpdateRequest;
import com.gachisa.product.entity.Product;
import com.gachisa.product.entity.ProductStatus;
import com.gachisa.product.repository.ProductRepository;
import com.gachisa.user.entity.User;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_SELLER_ID = 200L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, userRepository);
    }

    @Test
    void createProductSetsStockFromRequest() {
        Category category = category(CATEGORY_ID, "생활/리빙");
        User seller = seller(SELLER_ID, "판매자1");
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));
        given(userRepository.getReferenceById(SELLER_ID)).willReturn(seller);
        ProductCreateRequest request = new ProductCreateRequest("텀블러", "보온 텀블러", 15000, 30, CATEGORY_ID);

        ProductResponse response = productService.createProduct(SELLER_ID, request, "http://img/1.png");

        assertThat(response.stock()).isEqualTo(30);
        assertThat(response.imageUrl()).isEqualTo("http://img/1.png");
    }

    @Test
    void createProductThrowsWhenSameSellerHasDuplicateName() {
        Category category = category(CATEGORY_ID, "생활/리빙");
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));
        given(productRepository.existsBySellerIdAndName(SELLER_ID, "텀블러")).willReturn(true);
        ProductCreateRequest request = new ProductCreateRequest("텀블러", "보온 텀블러", 15000, 30, CATEGORY_ID);

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, request, "http://img/1.png"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);
    }

    @Test
    void createProductThrowsWhenCategoryNotFound() {
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.empty());
        ProductCreateRequest request = new ProductCreateRequest("텀블러", "desc", 1000, 10, CATEGORY_ID);

        assertThatThrownBy(() -> productService.createProduct(SELLER_ID, request, null))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void getProductReturnsProductWithStock() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 25);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(PRODUCT_ID);

        assertThat(response.id()).isEqualTo(PRODUCT_ID);
        assertThat(response.stock()).isEqualTo(25);
    }

    @Test
    void getProductThrowsWhenNotFound() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void searchProductsDelegatesFiltersToRepository() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.search(null, CATEGORY_ID, 1000, 20000, "원두")).willReturn(List.of(product));

        List<ProductResponse> responses = productService.searchProducts(CATEGORY_ID, 1000, 20000, "원두");

        assertThat(responses).hasSize(1);
        verify(productRepository).search(null, CATEGORY_ID, 1000, 20000, "원두");
    }

    @Test
    void updateProductUpdatesOnlyProvidedFields() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, 18000, null, null);

        ProductResponse response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request);

        assertThat(response.basePrice()).isEqualTo(18000);
        assertThat(response.name()).isEqualTo("텀블러");
        assertThat(response.stock()).isEqualTo(10);
    }

    @Test
    void updateProductUpdatesStock() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, 40, null);

        ProductResponse response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request);

        assertThat(response.stock()).isEqualTo(40);
    }

    @Test
    void updateProductThrowsWhenNewNameDuplicatedForSameSeller() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(productRepository.existsBySellerIdAndNameAndIdNot(SELLER_ID, "머그컵", PRODUCT_ID)).willReturn(true);
        ProductUpdateRequest request = new ProductUpdateRequest("머그컵", null, null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);
    }

    @Test
    void updateProductAllowsKeepingSameName() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest("텀블러", null, null, null, null);

        ProductResponse response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request);

        assertThat(response.name()).isEqualTo("텀블러");
    }

    @Test
    void updateProductThrowsWhenNotOwner() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest("해킹시도", null, null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, OTHER_SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateProductThrowsWhenNewCategoryNotFound() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, 999L);

        assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void deleteProductStopsSaleWhenOwner() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        productService.deleteProduct(PRODUCT_ID, SELLER_ID);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SUSPENDED);
    }

    @Test
    void deleteProductThrowsWhenNotOwner() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID, OTHER_SELLER_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void resumeProductResumesSaleWhenOwner() {
        Product product = product(PRODUCT_ID, SELLER_ID, ProductStatus.SUSPENDED, 10);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        ProductResponse response = productService.resumeProduct(PRODUCT_ID, SELLER_ID);

        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE.name());
    }

    private Product product(Long id, Long sellerId, ProductStatus status, int stock) {
        Product product = Product.builder()
                .seller(seller(sellerId, "판매자1"))
                .category(category(CATEGORY_ID, "생활/리빙"))
                .name("텀블러")
                .description("보온 텀블러")
                .basePrice(15000)
                .stock(stock)
                .imageUrl("http://img/1.png")
                .status(status)
                .createdAt(NOW)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private Category category(Long id, String name) {
        Category category = Category.builder().name(name).parent(null).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private User seller(Long id, String name) {
        User user = User.builder()
                .email(id + "@test.com")
                .password("encoded")
                .name(name)
                .role(UserRole.ROLE_SELLER)
                .createdAt(NOW)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
