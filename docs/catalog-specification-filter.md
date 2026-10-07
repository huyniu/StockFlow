# Specification filter and buying help

Storefront's **Lọc theo thông số** applies one specification name/value pair alongside keyword, category, brand, price and sorting. Suggestions come from stored active product/version specifications via `GET /api/v1/products/specification-options`; customers can type a value if suggestions are unavailable. The result URL preserves the filter, and the filter chip can remove it.

`GET /api/v1/products?specificationName=RAM&specificationValue=8GB&grouped=true` filters before pagination. Names are matched case-insensitively; values match exactly after removing ordinary spaces and ignoring case, so `8 GB` matches `8GB`, but not `128GB`. Both parameters are required together. This does not infer specifications from model names.

Grouped catalog pages match an active, enabled, non-archived SKU/version. Version-specific specifications override the parent model's value. A base value is inherited only when that version has no override for the specification name. Existing callers that omit both parameters keep their old behavior. No database migration is required.

The buying-help page includes an expandable FAQ covering OTP, filtering, fees, VNPay Sandbox, GHN Sandbox, cancellation, reviews and support. Its instructions distinguish Sandbox, simulated payments/tracking and actual shipping, and do not promise an automated real refund or unconfirmed warranty policy.
