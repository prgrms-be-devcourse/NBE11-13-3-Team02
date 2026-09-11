package com.gachisa.product.controller;

import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.global.storage.ImageStorageService;
import com.gachisa.product.dto.ProductCreateRequest;
import com.gachisa.product.dto.ProductResponse;
import com.gachisa.product.dto.ProductUpdateRequest;
import com.gachisa.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Product", description = "상품 등록/조회/검색/수정/삭제. 등록/수정/삭제/이미지변경은 판매자 전용이며 본인 상품만 가능합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;
    private final ImageStorageService imageStorageService;

    @Operation(summary = "상품 등록 (판매자 전용)",
            description = "multipart/form-data로 전송합니다. 'data' 파트에 ProductCreateRequest를 JSON으로, " +
                    "'image' 파트에 이미지 파일을 담습니다(이미지는 선택). 같은 판매자 내에서 상품명이 중복되면 거절됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponse createProduct(@AuthenticationPrincipal CustomUserDetails userDetails,
                                          @Parameter(description = "ProductCreateRequest를 JSON으로 담은 파트")
                                          @Valid @RequestPart("data") ProductCreateRequest request,
                                          @Parameter(description = "상품 이미지 파일 (선택)")
                                          @RequestPart(value = "image", required = false) MultipartFile image) {
        String imageUrl = (image != null && !image.isEmpty()) ? imageStorageService.store(image) : null;
        return productService.createProduct(userDetails.getUserId(), request, imageUrl);
    }

    @Operation(summary = "전체 상품 목록 조회", description = "인증 불필요.")
    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getProducts();
    }

    @Operation(summary = "내 상품 목록 조회 (판매자 전용)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my")
    @PreAuthorize("hasRole('SELLER')")
    public List<ProductResponse> getMyProducts(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return productService.getMyProducts(userDetails.getUserId());
    }

    @Operation(summary = "내 상품 검색 (판매자 전용)", description = "categoryId/minPrice/maxPrice/keyword는 모두 선택이며 AND 조건으로 필터링됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my/search")
    @PreAuthorize("hasRole('SELLER')")
    public List<ProductResponse> searchMyProducts(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                   @Parameter(description = "카테고리 ID") @RequestParam(required = false) Long categoryId,
                                                   @Parameter(description = "최소 가격(0 이상)") @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                   @Parameter(description = "최대 가격(0 이상)") @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                   @Parameter(description = "상품명 검색어") @RequestParam(required = false) String keyword) {
        return productService.searchMyProducts(userDetails.getUserId(), categoryId, minPrice, maxPrice, keyword);
    }

    @Operation(summary = "상품 단건 조회", description = "인증 불필요.")
    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "전체 상품 검색", description = "인증 불필요. categoryId/minPrice/maxPrice/keyword는 모두 선택이며 AND 조건으로 필터링됩니다.")
    @GetMapping("/search")
    public List<ProductResponse> searchProducts(@Parameter(description = "카테고리 ID") @RequestParam(required = false) Long categoryId,
                                                 @Parameter(description = "최소 가격(0 이상)") @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                 @Parameter(description = "최대 가격(0 이상)") @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                 @Parameter(description = "상품명 검색어") @RequestParam(required = false) String keyword) {
        return productService.searchProducts(categoryId, minPrice, maxPrice, keyword);
    }

    @Operation(summary = "상품 수정 (판매자 전용)", description = "본인 상품만 수정 가능. 요청 바디에 값을 보낸 필드만 변경됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponse updateProduct(@AuthenticationPrincipal CustomUserDetails userDetails,
                                          @PathVariable Long productId,
                                          @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(productId, userDetails.getUserId(), request);
    }

    @Operation(summary = "상품 판매 중지 (판매자 전용)", description = "실제 삭제가 아닌 소프트 삭제(SUSPENDED)입니다. 본인 상품만 가능.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SELLER')")
    public void deleteProduct(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long productId) {
        productService.deleteProduct(productId, userDetails.getUserId());
    }

    @Operation(summary = "상품 이미지 변경 (판매자 전용)", description = "multipart/form-data의 'image' 파트로 새 이미지 파일을 전송합니다. 본인 상품만 가능.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponse updateProductImage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable Long productId,
                                               @Parameter(description = "새 상품 이미지 파일")
                                               @RequestPart("image") MultipartFile image) {
        String imageUrl = imageStorageService.store(image);
        return productService.updateProductImage(productId, userDetails.getUserId(), imageUrl);
    }

    @Operation(summary = "상품 판매 재개 (판매자 전용)", description = "판매 중지(SUSPENDED) 상태의 상품을 다시 ON_SALE로 되돌립니다. 본인 상품만 가능.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{productId}/resume")
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponse resumeProduct(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long productId) {
        return productService.resumeProduct(productId, userDetails.getUserId());
    }
}
