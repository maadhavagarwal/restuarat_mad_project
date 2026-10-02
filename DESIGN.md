# Restro design system

## Product
English-language Android food delivery for Indian customers, phone first with landscape and tablet support. Food photography leads; navigation and purchase decisions remain legible.

## Direction
Charcoal canvas, natural food photography and restrained frosted overlays. Signature: a photograph stays sharp while its lower edge passes beneath a frosted restaurant caption. No artificial food art, fake reviews or decorative metrics.

## Tokens
Runtime owner: mobile ui/RestroTheme.kt. Canvas #101113, glass #252629 at translucent opacity, text #F8F6F2, secondary #B8B7B3, coral #FF946C, success #A7D8AB. Rounded 24dp panels; 16dp fields; 8/16/24/32dp spacing. Serif display headings, native sans body and tabular prices. Body 16sp, minimum 48dp actions.

## Canonical UI map
Theme and glass: RestroTheme.kt. Navigation, forms and feedback: DeliveryApp.kt. Async/session/cart owner: DeliveryViewModel. Data transport: DeliveryApi. Server quote/order authority: DeliveryService. Native Material dialogs and fields own accessibility and keyboard behavior. Scrollable content reserves navigation and system insets. Photography comes from catalog HTTPS URLs via Coil.

## Motion and fallback
Use platform animation scale. Backdrop captures exclude overlays; API 31+ RenderEffect blurs the sampled content. Older systems retain translucent bordered surfaces with stronger tint. Never blur foreground text. Glass appears on navigation and photograph overlays, with quiet solid form surfaces.
