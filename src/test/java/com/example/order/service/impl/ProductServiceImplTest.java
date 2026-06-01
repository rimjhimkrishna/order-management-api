package com.example.order.service.impl;

import com.example.order.dto.request.ProductRequest;
import com.example.order.dto.response.PageResponse;
import com.example.order.dto.response.ProductResponse;
import com.example.order.exception.ProductNotFoundException;
import com.example.order.model.Product;
import com.example.order.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductServiceImpl}.
 * Covers CRUD operations, pagination, and exception handling.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(BigDecimal.valueOf(29.99))
                .stockQuantity(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========================
    // Get Products Tests
    // ========================

    @Nested
    @DisplayName("Get Products")
    class GetProductsTests {

        @Test
        @DisplayName("Should return paginated products successfully")
        void shouldReturnPaginatedProducts() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(testProduct), pageable, 1);
            when(productRepository.findAll(pageable)).thenReturn(productPage);

            // Act
            PageResponse<ProductResponse> response = productService.getAllProducts(pageable);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getPageNumber()).isEqualTo(0);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo("Wireless Mouse");
        }

        @Test
        @DisplayName("Should return product by ID")
        void shouldReturnProductById() {
            // Arrange
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // Act
            ProductResponse response = productService.getProductById(1L);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Wireless Mouse");
            assertThat(response.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(29.99));
            assertThat(response.getStockQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should throw ProductNotFoundException for non-existent ID")
        void shouldThrowProductNotFoundException() {
            // Arrange
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ========================
    // Create Product Tests
    // ========================

    @Nested
    @DisplayName("Create Product")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product successfully")
        void shouldCreateProductSuccessfully() {
            // Arrange
            ProductRequest request = ProductRequest.builder()
                    .name("Mechanical Keyboard")
                    .description("RGB mechanical keyboard")
                    .price(BigDecimal.valueOf(89.99))
                    .stockQuantity(50)
                    .build();

            Product savedProduct = Product.builder()
                    .id(2L)
                    .name("Mechanical Keyboard")
                    .description("RGB mechanical keyboard")
                    .price(BigDecimal.valueOf(89.99))
                    .stockQuantity(50)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            ProductResponse response = productService.createProduct(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(2L);
            assertThat(response.getName()).isEqualTo("Mechanical Keyboard");
            assertThat(response.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(89.99));
            verify(productRepository, times(1)).save(any(Product.class));
        }
    }

    // ========================
    // Update Product Tests
    // ========================

    @Nested
    @DisplayName("Update Product")
    class UpdateProductTests {

        @Test
        @DisplayName("Should update product successfully")
        void shouldUpdateProductSuccessfully() {
            // Arrange
            ProductRequest updateRequest = ProductRequest.builder()
                    .name("Updated Mouse")
                    .description("Updated ergonomic mouse")
                    .price(BigDecimal.valueOf(34.99))
                    .stockQuantity(150)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            ProductResponse response = productService.updateProduct(1L, updateRequest);

            // Assert
            assertThat(response.getName()).isEqualTo("Updated Mouse");
            assertThat(response.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(34.99));
            assertThat(response.getStockQuantity()).isEqualTo(150);
        }

        @Test
        @DisplayName("Should throw ProductNotFoundException when updating non-existent product")
        void shouldThrowWhenUpdatingNonExistentProduct() {
            // Arrange
            ProductRequest request = ProductRequest.builder()
                    .name("Ghost Product")
                    .price(BigDecimal.valueOf(10.00))
                    .stockQuantity(5)
                    .build();

            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.updateProduct(999L, request))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ========================
    // Delete Product Tests
    // ========================

    @Nested
    @DisplayName("Delete Product")
    class DeleteProductTests {

        @Test
        @DisplayName("Should delete product successfully")
        void shouldDeleteProductSuccessfully() {
            // Arrange
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            doNothing().when(productRepository).delete(testProduct);

            // Act
            productService.deleteProduct(1L);

            // Assert
            verify(productRepository, times(1)).delete(testProduct);
        }

        @Test
        @DisplayName("Should throw ProductNotFoundException when deleting non-existent product")
        void shouldThrowWhenDeletingNonExistentProduct() {
            // Arrange
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).delete(any());
        }
    }
}
