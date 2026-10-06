# VNPay IPN setup

StockFlow exposes `GET /api/v1/payments/vnpay/ipn` without JWT. Every notification is authenticated by HMAC-SHA512 and validated against merchant, stored transaction reference, amount and payment status before any update. The response is JSON with `RspCode` and `Message`, never a browser redirect.

- `00`: notification processed (including a declined payment; this is acknowledgement, not proof the payment succeeded).
- `02`: transaction already processed, including when the legacy Return handler processed it first.
- `01`: transaction reference not found.
- `04`: payment amount mismatch.
- `97`: invalid signature/merchant or repeated query parameters.
- `99`: malformed notification or processing failure; transactional changes roll back so VNPay can retry.

IPN and the existing Return flow share the same locked transaction processing. Replays never dispatch stock again. The existing Return endpoint is kept compatible with the current frontend and tests.

## Configure the portal

1. Restart StockFlow to expose the new endpoint on port 8080.
2. Expose that port through an HTTPS tunnel or deploy it on a public HTTPS host.
3. Enter `https://<public-host>/api/v1/payments/vnpay/ipn` as IPN URL in VNPay Merchant Admin, protocol GET and hash HMACSHA512.
4. Test IPN through the portal, then save. An unsigned test request receives `97`; that proves connectivity only, not a successful payment notification. A successful signed test also needs a transaction previously created by StockFlow.
5. For a full remote browser test, set `VNPAY_RETURN_URL=https://<public-host>/api/v1/payments/vnpay/return` and `VNPAY_STOREFRONT_URL=https://<public-host>/`, then restart the app. Local browser tests can keep the localhost Return URL.

Do not put the Return URL into the IPN field. VNPay's server cannot reach localhost on your computer. Keep the app and tunnel running while testing; if the tunnel hostname changes, update the portal's IPN URL.

This implementation does not establish that error 70 is caused by IPN configuration or that saving IPN will resolve it.

Reference: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
