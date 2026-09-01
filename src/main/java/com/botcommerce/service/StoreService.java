package com.botcommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.botcommerce.dto.store.StoreDtos.*;
import com.botcommerce.model.Merchant;
import com.botcommerce.model.Product;
import com.botcommerce.model.ProductCategory;
import com.botcommerce.repository.MerchantRepository;
import com.botcommerce.repository.ProductCategoryRepository;
import com.botcommerce.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoreService {

	private final MerchantRepository merchantRepository;
	private final ProductRepository productRepository;
	private final ProductCategoryRepository categoryRepository;

	public StoreResponse getStore(String slug) {
		Merchant merchant = merchantRepository.findBySlug(slug)
				.orElseThrow(() -> new RuntimeException("Store not found"));

		if (!merchant.getIsActive()) {
			throw new RuntimeException("Store is not active");
		}

		List<ProductCategory> categories = categoryRepository.findByMerchantIdOrderBySortOrder(merchant.getId());

		List<Product> products = productRepository.findAvailableByMerchantId(merchant.getId());

		StoreResponse res = new StoreResponse();
		res.setId(merchant.getId().toString());
		res.setBusinessName(merchant.getBusinessName());
		res.setSlug(merchant.getSlug());
		res.setCategory(merchant.getCategory());
		res.setDescription(merchant.getDescription());
		res.setCity(merchant.getCity());
		res.setLogoUrl(merchant.getLogoUrl());
		res.setOnline(merchant.getIsOnline());
		res.setMinOrderAmount(merchant.getMinOrderAmount());
		res.setDeliveryFee(merchant.getDeliveryFee());
		res.setDeliveryAreas(merchant.getDeliveryAreas());
		res.setTotalOrders(merchant.getTotalOrders());

		res.setCategories(categories.stream().map(this::toCategoryResponse).toList());
		res.setProducts(products.stream().map(this::toProductResponse).toList());

		return res;
	}

	private StoreCategoryResponse toCategoryResponse(ProductCategory cat) {
		StoreCategoryResponse res = new StoreCategoryResponse();
		res.setId(cat.getId().toString());
		res.setName(cat.getName());
		res.setSortOrder(cat.getSortOrder());
		return res;
	}

	private StoreProductResponse toProductResponse(Product p) {
		StoreProductResponse res = new StoreProductResponse();
		res.setId(p.getId().toString());
		res.setName(p.getName());
		res.setDescription(p.getDescription());
		res.setPrice(p.getPrice());
		res.setCompareAtPrice(p.getCompareAtPrice());
		res.setImageUrl(p.getImageUrl());
		res.setAvailable(p.getIsAvailable());
		res.setTags(p.getTags());
		if (p.getCategory() != null) {
			res.setCategoryId(p.getCategory().getId().toString());
			res.setCategoryName(p.getCategory().getName());
		}
		return res;
	}
}