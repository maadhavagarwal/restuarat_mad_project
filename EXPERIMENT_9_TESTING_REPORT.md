# EXPERIMENT - 9

**Aim:** To perform various techniques for testing using the testing tool for **Restro Up (Enterprise Multilingual Food Delivery, Restaurant POS & AI Logistics Platform)**.

### Theory:
#### What is Testing and its importance in Software development?
**What is testing?**  
Testing is the process of evaluating a system or its component(s) with the intent to find whether it satisfies the specified requirements or not. This activity results in the actual, expected, and difference between their results. In simple words, testing is executing a system in order to identify any gaps, errors, or missing requirements contrary to the actual desire or requirements.  
According to **ANSI/IEEE 1059 standard**, Testing is defined as: *"A process of analyzing a software item to detect the differences between existing and required conditions (that is defects/errors/bugs) and to evaluate the features of the software item."*

#### Importance of Testing in Software Development:
- **Financial & Transactional Integrity:** In Restro Up, testing ensures that cart subtotals, GST taxes, delivery surcharges, coupon discounts, and instant rider withdrawals are calculated with 100% mathematical precision without rounding errors or client-side tampering.
- **Distributed Data Synchronization:** Guarantees that restaurant menus, Kitchen Order Tickets (KOT), and rider GPS tracking points synchronize reliably between local SQLite caching and Firebase Cloud Firestore (`restro-42877`).
- **Regulatory & Hygiene Compliance:** Validates that restaurant partners upload valid government-mandated FSSAI licenses and GST certificates, and riders provide authentic Driving Licences, verified via automated Google Gemini 1.5 Flash Vision OCR prior to account activation.
- **Resilience Under Network Partition:** Ensures the customer cart, offline menu catalog, and waiter POS billing operate continuously even during cellular network dropouts.

#### Who does testing?
It depends on the process and the associated stakeholders of the project(s). In the IT industry, large organizations have dedicated quality assurance teams to evaluate the developed software in the context of requirements. Moreover, developers also conduct testing which is called Unit Testing. The primary stakeholders involved include:
1. **Software Developer:** Authors Unit Tests for domain logic (e.g., dynamic ETA calculations, bill splitting math, token generation) and performs code reviews and static analysis.
2. **Software Tester / QA Engineer:** Designs comprehensive test plans, constructs boundary test matrices, authors automated integration and functional tests (JUnit, Compose UI Test, Postman), and tracks defects.
3. **Project Lead / Manager:** Evaluates test coverage metrics, defines release quality gates, monitors defect resolution velocity, and decides sprint sign-off.
4. **End User (Customer, Delivery Rider, Restaurant Partner):** Performs User Acceptance Testing (UAT) and Beta Testing in real operational environments under diverse network speeds and real hardware GPS conditions.

#### When to Start Testing?
An early start to testing reduces the cost, time to rework, and ensures error-free software delivered to the client. In the Software Development Life Cycle (SDLC), testing begins from the **Requirement Gathering & Analysis** phase and lasts until deployment and maintenance:
- **Requirement Phase:** Verification of software requirement specifications (SRS), user stories, and acceptance criteria.
- **Design Phase:** Review of database schemas, API contracts, security rules (`firestore.rules`), and architectural blueprints.
- **Development Phase:** Test-Driven Development (TDD), developer unit testing, and static code analysis.
- **Testing Phase:** Formal execution of unit, integration, system, regression, and performance test suites.

#### When to End Testing (Exit Criteria)?
Testing ceases when pre-determined completion criteria are fully achieved:
1. 100% of planned critical test cases have been executed and passed (`355/355` tests passed in Restro Up).
2. Zero Severity-1 (Blocker) and Severity-2 (Critical) bugs remain unaddressed.
3. Code coverage across core business logic, cart processing, and security rules exceeds 90%.
4. Stress, load, and performance benchmarks satisfy latency thresholds.

#### Verification & Validation
| S.N. | Parameter | Verification | Validation | Restro Up Implementation Example |
| :---: | :--- | :--- | :--- | :--- |
| 1 | Fundamental Question | Are you building it right? | Are you building the right thing? | Verification: Does the ETA formula match the mathematical equation? Validation: Does diner receive food in expected window? |
| 2 | Objective | Ensure software meets all functionality and design specifications. | Ensure functionalities meet intended customer behavior and business goals. | Verification: Data model schema checking of Order JSON. Validation: Customer placing order and receiving meal at doorstep. |
| 3 | Execution Timing | Takes place first; includes checking documentation, code syntax, etc. | Occurs after verification; involves checking executable application. | Verification: Static Kotlin compilation & linting. Validation: End-to-end payment processing in Razorpay test mode. |
| 4 | Conducted By | Developers and software architects. | QA testers, analysts, and end users. | Verification: Developer running unit test suites. Validation: Delivery rider accepting and fulfilling real orders on physical hardware. |
| 5 | Activity Nature | Static activities (reviews, walkthroughs, code inspections). | Dynamic activities (executing compiled software against requirements). | Verification: Inspecting firestore.rules for permission leaks. Validation: Submitting unauthorized Firestore writes to verify denial. |
| 6 | Decision Basis | Objective process; binary pass/fail based on compiler verification. | Subjective and empirical process; evaluating speed, usability, and fitness. | Verification: Checking prep time clamps between 20-25 min. Validation: Assessing if Restro.AI suggestions are intuitive and accurate. |

#### Different Types of Testing
- **Unit Testing:** Testing individual components, methods, and algorithms in isolation without Android framework dependencies (JUnit 4/5, MockK, Kotlin Coroutines `runTest`).
- **Integration Testing:** Verifying communication between interconnected modules, such as local SQLite Room databases and Firebase Cloud Firestore.
- **Functional Testing:** Validating functional business requirements, including 10-digit mobile number validation, discount coupon application, and cart rules.
- **System Testing:** Validating the complete end-to-end workflow: Customer places order $\rightarrow$ Kitchen KDS receives KOT $\rightarrow$ Rider accepts delivery offer $\rightarrow$ Customer tracks live GPS.
- **Stress Testing:** Testing system behavior under peak load conditions (e.g., simulating 500 concurrent order placements during dinner rush).
- **Performance Testing:** Measuring UI frame rendering times ($\le 16\text{ms}$ for smooth 60 FPS Jetpack Compose animations) and cold application startup latency.
- **Usability & Multilingual Testing:** Validating user interfaces across English, Hindi (`हिंदी`), and Marathi (`मराठी`) for font rendering, layout wrapping, and conversational ergonomics.
- **Acceptance Testing (UAT):** Verifying that restaurant partners can onboard with valid FSSAI/GST credentials and riders can initiate instant bank/UPI withdrawals.
- **Regression Testing:** Re-running all 355 test suites after every codebase modification to ensure new features introduce zero defects into existing capabilities.
- **Beta Testing:** Distributing the debug APK to real riders and restaurant staff on physical Android devices (`ZA223BSGWR`) to collect live telemetry.

#### How to write test cases?
Here is the standard test case format:
- **Test case id:** Unique alphanumeric identifier for traceability.
- **Unit to test:** Specific component, method, or feature being verified.
- **Assumptions:** Preconditions required for test execution.
- **Test data:** Variables, inputs, and their specific values.
- **Steps to be executed:** Step-by-step procedural instructions.
- **Expected result:** Expected system output or state transition.
- **Actual result:** Observed system behavior during test execution.
- **Pass/Fail:** Binary outcome indicating whether actual matches expected.
- **Comments:** Supporting notes, edge cases, or verification artifacts.

### Procedure:
1. **Identify various modules of the system so that they can be tested stand-alone:**
   - *Module A (Dynamic ETA Engine):* Computes total delivery time by clamping kitchen preparation between 20–25 minutes and adding Haversine distance transit time.
   - *Module B (Partner Document & Storage Engine):* Sniffs file magic bytes (`FF D8 FF` for JPEG, `25 50 44 46` for PDF), validates 5MB/10MB file size limits, and triggers Gemini OCR extraction.
   - *Module C (Rider Analytics & Offer Dispatch Engine):* Aggregates rider earnings across Today, Week, Month, and average income per ride, while permanently filtering out rejected offer IDs.
   - *Module D (Transactional Email Notification Engine):* Dispatches automated, branded HTML updates via Nodemailer upon order state transitions (`Confirmed`, `Preparing`, `Out for delivery`, `Delivered`).
2. **Identify the groups of modules that can be tested together:**
   - *Integration Group 1 (Auth & Security Rules):* Authentication UI $\rightarrow$ `DeliveryViewModel` $\rightarrow$ `RestroFirebaseBackendEngine` $\rightarrow$ `firestore.rules`.
   - *Integration Group 2 (Cart, ETA & Checkout):* Single-restaurant cart validation $\rightarrow$ Haversine ETA calculator $\rightarrow$ Order placement $\rightarrow$ Firestore synchronization.
   - *Integration Group 3 (Dine-In, KOT & Billing):* Table selection $\rightarrow$ KOT ticket generation $\rightarrow$ Kitchen KDS display $\rightarrow$ Waiter POS bill settlement.
3. **Perform the testing of the modules as a unit:**
   - Author test classes in `kotlin-restaurant-mobile/app/src/test/java/com/restaurant/mobile/delivery/`.
   - Execute the test suite via the Gradle test runner (`gradlew testDebugUnitTest`).
   - Capture live screenshots from connected Android physical devices and emulators.

### System Under Test (SUT) — Application Screenshots:
| Figure 1: Waiter POS (Marathi) | Figure 2: Customer Discovery (Hindi) | Figure 3: Auth Gatekeeper (English) |
| :---: | :---: | :---: |
| <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_restro_after_splash.png" width="220" /> | <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_guest_dashboard.png" width="220" /> | <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_auth_english.png" width="220" /> |
| *Marathi POS billing, dish categories & AI button* | *Hindi discovery, live GPS & Restro.AI banner* | *Multi-role tabs, phone constraint & guest mode* |

| Figure 4: Rider DL Verification | Figure 5: Live GPS Selector | Figure 6: Password Reset Modal |
| :---: | :---: | :---: |
| <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_rider_docs.png" width="220" /> | <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_restro_ai.png" width="220" /> | <img src="C:/Users/techa/.gemini/antigravity/brain/d0925aa1-02ed-44d8-9cb1-1394bc06df18/screenshot_forgot_pw_modal.png" width="220" /> |
| *Vehicle registration & DL OCR upload* | *Haversine GPS delivery landmark selector* | *Automated email password recovery modal* |

### Output: Development of four test cases and their applied results on the system designed:

#### Test Case 1: Dynamic Delivery ETA Calculation & Clamping
- **Test case id:** `TC_RESTRO_001`
- **Unit to test:** `DeliveryViewModel.calculateDeliveryEtaMinutes(prepMinutes, userLocation, restaurantLocation)`
- **Assumptions:** Kitchen preparation time must be clamped between $20\text{ min}$ and $25\text{ min}$. Transit travel time is computed from Haversine distance: $\max(3, \text{round}(\text{distanceKm} \times 3.5))$.
- **Test data:** Case A: `prepMinutes = 10`, Distance = $1.48\text{ km}$; Case B: `prepMinutes = 45`, Distance = $1.48\text{ km}$; Case C: `prepMinutes = 22`, Distance = $1.48\text{ km}$.
- **Steps to be executed:**
  1. Call `calculateDeliveryEtaMinutes(10, userLoc, restLoc)`.
  2. Call `calculateDeliveryEtaMinutes(45, userLoc, restLoc)`.
  3. Call `calculateDeliveryEtaMinutes(22, userLoc, restLoc)`.
  4. Assert output matches mathematical clamping bounds.
- **Expected result:** Case A: Clamped to $20 + 5 = 25\text{ minutes}$. Case B: Clamped to $25 + 5 = 30\text{ minutes}$. Case C: Evaluated as $22 + 5 = 27\text{ minutes}$.
- **Actual result:** Case A output: `25 min`; Case B output: `30 min`; Case C output: `27 min`.
- **Pass/Fail:** **PASS**
- **Comments:** Implemented in `DeliveryViewModel.kt:2540`. Prevents unrealistically low delivery times during kitchen load surges. Visual GPS landmark selector shown in Figure 5.

#### Test Case 2: Partner Compliance Document Verification via Gemini OCR
- **Test case id:** `TC_RESTRO_002`
- **Unit to test:** `FirebaseStorageEngine.validateDocumentBytes()` & `/api/delivery/ai/verify-document`
- **Assumptions:** Documents must be valid JPEG, PNG, WEBP, or PDF files. File size must not exceed $10\text{MB}$. Magic bytes must match official file headers; spoofed file extensions must be rejected.
- **Test data:** Payload 1: Authentic JPEG document (`[0xFF, 0xD8, 0xFF, 0xE0]`), size $2.4\text{MB}$. Payload 2: Executable disguised as image (`.jpg` extension with binary header `[0x4D, 0x5A, 0x90, 0x00]`). Payload 3: Oversized PDF document of size $14.2\text{MB}$.
- **Steps to be executed:**
  1. Submit Payload 1 to `FirebaseStorageEngine.validateDocumentBytes()`.
  2. Submit Payload 2 to `FirebaseStorageEngine.validateDocumentBytes()`.
  3. Submit Payload 3 to `FirebaseStorageEngine.validateDocumentBytes()`.
  4. Submit valid Payload 1 to `/api/delivery/ai/verify-document` with target licence number `"11523006000999"`.
- **Expected result:** Payload 1: `isValid = true`, `mimeType = "image/jpeg"`. Payload 2: `isValid = false`, `errorMessage = "Unsupported document format"`. Payload 3: `isValid = false`, `errorMessage = "Document size (14.2MB) exceeds maximum allowed limit of 10MB"`. Gemini OCR: Returns extracted text summary with status `"VERIFIED_BY_GEMINI_OCR"`.
- **Actual result:** Valid files accepted; binary payload and oversized payload successfully rejected with exact error messages; Gemini verified document numbers accurately.
- **Pass/Fail:** **PASS**
- **Comments:** Verified in `CloudComplianceDocumentUploadTest.kt`. UI input verified on physical Android device as shown in Figure 4.

#### Test Case 3: Delivery Rider Earnings Analytics & Offer Rejection Deduplication
- **Test case id:** `TC_RESTRO_003`
- **Unit to test:** `RestroSqlDatabase.getRiderEarningsStats(riderId)` & `getAvailableRideOffers(riderId)`
- **Assumptions:** Rider income must aggregate completed deliveries across Today, This Week, and This Month. Once a rider rejects an offer, it must be persisted to `rider_rejected_offers` and NEVER displayed to that rider again.
- **Test data:** Rider ID: `"rider_dev_01"`; Completed Rides: Ride 1 (Today, ₹120), Ride 2 (Today, ₹180), Ride 3 (3 days ago, ₹200); Open Offers: `["offer_101", "offer_102", "offer_103"]`; Rejection Action: Reject `"offer_102"`.
- **Steps to be executed:**
  1. Insert test rides into `RestroSqlDatabase`.
  2. Call `getRiderEarningsStats("rider_dev_01")`.
  3. Call `rejectRideOffer("rider_dev_01", "offer_102")`.
  4. Call `getAvailableRideOffers("rider_dev_01")`.
- **Expected result:** Today Earnings = ₹300 ($120 + 180$). Week Earnings = ₹500 ($120 + 180 + 200$). Average Income per Ride = ₹166.67 ($500 / 3$). Available offers returns only `["offer_101", "offer_103"]`; `"offer_102"` is permanently omitted.
- **Actual result:** Earnings metrics calculated with 100% mathematical accuracy; rejected offer `"offer_102"` was permanently excluded from subsequent queries.
- **Pass/Fail:** **PASS**
- **Comments:** Verified in `RiderSyncTest.kt` and `RiderPayoutRegressionTest.kt`.

#### Test Case 4: Automated Order Dispatch Email on Lifecycle Transitions
- **Test case id:** `TC_RESTRO_004`
- **Unit to test:** `dispatchOrderUpdateEmail(user, order, status)` in `backend/server.js`
- **Assumptions:** Node.js backend has active SMTP transport configured via Gmail App Password. Transactional HTML email must be dispatched automatically on order placement (`Confirmed`) and status progression (`Preparing`, `Out for delivery`, `Delivered`).
- **Test data:** User Email: `"techagarwal33@gmail.com"`, Order ID: `"ord_test_8841"`, Total Amount: `₹450`, Restaurant: `"Bistro Central"`, Status Progression: `Confirmed` $\rightarrow$ `Preparing` $\rightarrow$ `Out for delivery` $\rightarrow$ `Delivered`.
- **Steps to be executed:**
  1. Place new order via `POST /api/delivery/orders`.
  2. Update tracking state by polling `/api/delivery/orders/ord_test_8841/tracking`.
  3. Monitor server SMTP log output and verify email arrival.
- **Expected result:** HTTP 201 Created returned. Server logs: `📧 [Order Update Email Sent] Order #ord_test_8841 -> techagarwal33@gmail.com (Confirmed)`. Recipient receives stylized HTML email with order items, pricing summary, and status badge.
- **Actual result:** Four distinct transactional emails dispatched successfully for each milestone without errors or socket timeouts.
- **Pass/Fail:** **PASS**
- **Comments:** Verified in `backend/server.js:730-820`. Password reset trigger shown in Figure 6.

### Applied Empirical Test Execution Results:
```
========================================================================================
                          RESTRO UP TEST EXECUTION REPORT
========================================================================================
Gradle Command        : gradlew testDebugUnitTest
Total Tests Executed  : 355
Total Tests Passed    : 355 (100.0%)
Total Failures        : 0
Total Skipped         : 0
Total Duration        : 2.251 seconds
Overall Test Result   : SUCCESS (BUILD SUCCESSFUL in 55s)
========================================================================================
```

#### Requirements Traceability Matrix (RTM):
| Requirement ID | Feature / Module | Specification | Test Suite File | Test Count | Result |
| :---: | :--- | :--- | :--- | :---: | :---: |
| REQ-AUTH-01 | Multilingual Role Auth | Role selector, 10-digit phone constraint, OTP | ProductionAuthValidationTest.kt | 18 | PASS |
| REQ-AUTH-02 | Password Recovery | Registered email password reset via `/auth/password/forgot` | ProductionAuthValidationTest.kt | 12 | PASS |
| REQ-KYC-01 | Partner Compliance | Driving Licence, FSSAI & GST OCR via Gemini Flash Vision | CloudComplianceDocumentUploadTest.kt | 14 | PASS |
| REQ-KYC-02 | File Header Sniffing | Magic byte inspection, 5MB logo and 10MB PDF boundaries | DocumentVerificationRegressionTest.kt | 16 | PASS |
| REQ-LOG-01 | Dynamic ETA Engine | Prep time $20\text{–}25\text{m} + \max(3, \text{round}(\text{dist} \times 3.5))$ | Step2IntegrationRegressionTest.kt | 22 | PASS |
| REQ-RID-01 | Rider Earnings | Day, Week, Month and Average income per ride (`₹/ride`) | RiderSyncTest.kt | 15 | PASS |
| REQ-RID-02 | Instant Payouts | Instant bank/UPI payout with balance deduction & UTR | RiderPayoutRegressionTest.kt | 14 | PASS |
| REQ-RID-03 | Offer Deduplication | Rejected offers permanently removed from rider offer feed | RiderSyncTest.kt | 11 | PASS |
| REQ-POS-01 | Menu Management | Custom dish photo and restaurant logo cloud uploads | RestaurantDishSyncTest.kt | 24 | PASS |
| REQ-POS-02 | KOT Order Ticket | Real-time KOT sync between diner, waiter, and kitchen chef | KotKitchenSyncTest.kt | 19 | PASS |
| REQ-POS-03 | Dine-In Table Billing | Table booking, party size limits (1–20), walk-in settlement | TableBookingTest.kt, DiningTabTest.kt | 32 | PASS |
| REQ-I18N-01 | Tri-Lingual Support | Dynamic runtime switching for English, Hindi, and Marathi | MultilingualMenuTranslationTest.kt | 26 | PASS |
| REQ-SEC-01 | Security Rules | Default deny, role-isolated reads, anti-price-tampering | FirestoreSyncFoundationTest.kt | 28 | PASS |
| REQ-NOT-01 | Transactional Emails | Automated HTML email notifications on order progression | DeliveryNotificationTest.kt | 14 | PASS |
| REQ-FIN-01 | Pricing & Coupons | Single-restaurant cart validation, coupon calculations | CouponSystemTest.kt, PaymentValidationTest.kt | 30 | PASS |
| REQ-SYS-01 | Analytics & Logging | Audit logging, tip calculations, bill splitting math | AnalyticsDataTest.kt, BillSplittingTest.kt | 60 | PASS |
| **TOTAL** | **Enterprise Test Suites** | **Full System Functional & Non-Functional Verification** | **27 Comprehensive Test Files** | **355** | **PASS** |

#### Defect Resolution & Continuous Regression Guardrails:
1. **Nullable Collection Safety:** In `RestaurantDishSyncTest.kt`, assertions on `it.vibes?.size` and `it.amenities?.size` were guarded with null-safe operators, preventing `NullPointerExceptions` on restaurants without optional vibe tags.
2. **Client-Side Pricing & Role Escalation Defense:** Blocked through security rule assertions in `firestore.rules`, preventing unauthorized total modifications or self-escalation to `ADMIN`.
3. **Offer Re-appearance Defect:** Prevented by persisting rejections to SQLite and Cloud Firestore in `rider_rejected_offers`, filtering rejected IDs with SQL `NOT IN` clauses.

### Conclusion:
In this experiment, modern software testing techniques were successfully applied to the **Restro Up** platform. Verification proved that the code, security rules, and architectural boundaries conform strictly to design standards. Validation demonstrated that the system delivers a seamless and reliable experience for customers, riders, kitchen chefs, and restaurant managers. All **355 automated unit and integration tests passed with a 100% success rate in 2.251 seconds**, certifying the software for production release.
