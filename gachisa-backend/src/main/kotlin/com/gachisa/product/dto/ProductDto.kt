package com.gachisa.product.dto

import com.gachisa.product.entity.Product
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime

@Schema(description = "상품 등록 요청 (multipart의 'data' 파트에 JSON으로 담아 전송)")
data class ProductCreateRequest(
    @field:Schema(description = "상품명 (같은 판매자 내에서 중복 불가)", example = "텀블러 6종 세트")
    @field:NotBlank
    val name: String,
    @field:Schema(description = "상품 설명", example = "보온·보냉 겸용 텀블러 6종 구성")
    val description: String?,
    @field:Schema(description = "정가(원). 0보다 커야 함", example = "18000")
    @field:Positive
    val basePrice: Int,
    @field:Schema(description = "재고 수량. 0 이상이어야 함", example = "100")
    @field:PositiveOrZero
    val stock: Int,
    @field:Schema(description = "카테고리 ID", example = "1")
    @field:NotNull
    val categoryId: Long?,
)

data class ProductPaymentInfo(
    val productId: Long,
    val basePrice: Int,
)

data class ProductResponse(
    val id: Long?,
    val sellerId: Long,
    val sellerName: String,
    val categoryId: Long,
    val categoryName: String,
    val name: String,
    val description: String?,
    val basePrice: Int,
    val stock: Int,
    val imageUrl: String?,
    val status: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        @JvmStatic
        fun of(product: Product): ProductResponse {
            return ProductResponse(
                id = product.id,
                sellerId = product.seller.id!!,
                sellerName = product.seller.name,
                categoryId = product.category.id!!,
                categoryName = product.category.name,
                name = product.name,
                description = product.description,
                basePrice = product.basePrice,
                stock = product.stock,
                imageUrl = product.imageUrl,
                status = product.status.name,
                createdAt = product.createdAt,
            )
        }
    }
}

// 부분 수정 DTO: 필드가 null이면 "변경하지 않음"을 의미하므로 @NotNull/@NotBlank는 붙이지 않는다
// (ProductService.updateProduct의 null 체크와 의미가 맞아야 함)
@Schema(description = "상품 수정 요청. 값을 보낸 필드만 변경되고, 생략(null)한 필드는 그대로 유지됨")
data class ProductUpdateRequest(
    @field:Schema(description = "상품명. 변경하지 않으려면 생략", example = "텀블러 6종 세트")
    val name: String?,
    @field:Schema(description = "상품 설명. 변경하지 않으려면 생략")
    val description: String?,
    @field:Schema(description = "정가(원). 0보다 커야 함", example = "18000")
    @field:Positive
    val basePrice: Int?,
    @field:Schema(description = "재고 수량. 0 이상이어야 함", example = "100")
    @field:PositiveOrZero
    val stock: Int?,
    @field:Schema(description = "카테고리 ID", example = "1")
    val categoryId: Long?,
)
