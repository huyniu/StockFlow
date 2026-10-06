# Customer reviews

After an order reaches DELIVERED, a CUSTOMER can open its details and click **Đánh giá sản phẩm / dịch vụ** beside each purchased SKU. The form accepts separate 1–5 ratings for the product and shopping/delivery service, plus a required comment of up to 2,000 characters. A submitted review can be viewed again but cannot be edited in this version.

`POST /api/v1/orders/{id}/reviews` takes `product_id`, `rating`, `service_rating`, `comment`. The server checks ownership, DELIVERED status and product membership while holding the order lock. V22 enforces one review per order/product and a foreign key to the purchased order item. A paid CONFIRMED order is not yet eligible.

`GET /api/v1/orders/{id}/reviews` is private to the customer who owns the order. Public `GET /api/v1/products/{id}/reviews?page=0` returns 20 entries per page, newest first, plus counts and average product/service ratings. Model pages include their SKU reviews. Public responses include the customer's display name, but do not include email, phone, customer ID or order ID. User-written text is rendered as escaped text.

The existing fulfillment, payment and inventory flows are unchanged. Existing reviews remain visible if an order is subsequently returned. Moderation, admin replies and review editing are not part of this first version.
