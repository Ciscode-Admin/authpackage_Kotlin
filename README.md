# Android Authentication Package —
Integration Guide

## 1. Requirements

```text
● Min SDK: 24+
● AndroidX: enabled
● Permissions: Internet + Custom Tabs (for OAuth)
● Backend Base URL: your backend URL (only the base changes; endpoints are fixed by
this package)
```

## 2. Gradle Setup

### 2.1. settings.gradle

Ensure you’re using Maven Central:

```gradle
dependencyResolutionManagement {
repositories {
google()
mavenCentral()
}
}
```

### 2.2. app/build.gradle

Add dependencies (note the MSAL exclude):

```gradle
dependencies {
implementation("io.github.ciscode-ma:authui:0.1.1")
implementation("com.microsoft.identity.client:msal:5.8.0") {
exclude(group = "com.microsoft.device.display", module =
"display-mask")
}
implementation("androidx.browser:browser:1.8.0")
}
```

## 3. Android Manifest & Network

### 3.1. Permissions

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission
android:name="android.permission.ACCESS_NETWORK_STATE"/>
```

(Other permissions like location, camera, ARCore are not required for auth.)

### 3.2. Application Attributes

```xml
android:usesCleartextTraffic="true"
android:networkSecurityConfig="@xml/network_security_config"
```

### 3.3. Activities & OAuth Deep Links

MainActivity (launcher + Google OAuth redirect):

```xml
<activity
android:name=".MainActivity"
android:exported="true"
android:label="@string/app_name"
android:launchMode="singleTask">
<!-- Launcher -->
<intent-filter>
<action android:name="android.intent.action.MAIN"/>
<category android:name="android.intent.category.LAUNCHER"/>
</intent-filter>
<!-- Google OAuth callback -->
<intent-filter>
<action android:name="android.intent.action.VIEW"/>
<category android:name="android.intent.category.DEFAULT"/>
<category android:name="android.intent.category.BROWSABLE"/>
<data android:scheme="restosoft"
android:host="auth"
android:path="/google/callback"/>
</intent-filter>
</activity>
```

MSAL BrowserTabActivity (replace host and path for your app):

```xml
<activity
android:name="com.microsoft.identity.client.BrowserTabActivity"
android:exported="true"
android:launchMode="singleTask">
<intent-filter>
<action android:name="android.intent.action.VIEW"/>
<category android:name="android.intent.category.DEFAULT"/>
<category android:name="android.intent.category.BROWSABLE"/>
<data android:scheme="msauth"
android:host="com.example.yourapp"
android:path="/ENCODED_SIGNATURE_HASH="/>
</intent-filter>
</activity>
```

### 3.4. Network Security Config (res/xml/network_security_config.xml)

Make sure to remove cleartextTrafficPermitted="true" in production builds and
enforce HTTPS only.

```xml
<network-security-config>
<domain-config cleartextTrafficPermitted="true">
<domain includeSubdomains="true">localhost</domain>
<domain includeSubdomains="true">10.0.2.2</domain>
</domain-config>
</network-security-config>
```

## 4. Microsoft Sign-In (MSAL)

### 4.1. MSAL Config (res/raw/msal_config.json)

```json
{
"client_id": "YOUR_AZURE_APP_CLIENT_ID",
"authorization_user_agent": "DEFAULT",
"redirect_uri":
"msauth://com.example.yourapp/ENCODED_SIGNATURE_HASH%3D",
"authorities": [
{
"type": "AAD",
"authority_url": "https://login.microsoftonline.com/consumers"
}
]
}
```

● client_id: from Azure app registration  
● redirect_uri: msauth://{package}/{ENCODED_SIGNATURE_HASH}  
● authority_url: keep .../consumers for personal accounts  

### 4.2. Azure Setup

```text
● Register an Azure AD app (personal accounts only)
● Add Mobile redirect URI = same as above
● Chose the supported account types to be “Personal Microsoft accounts only”
```

### 4.3. Signature Hash

Generate SHA-1:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list
-v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias
androiddebugkey -storepass android -keypass android
```

Convert SHA-1 → base64 URL-safe hash:

```powershell
$sha1 = "AA:BB:CC:DD:...:ZZ" Paste that SHA1 fingerprint here
$bytes = ($sha1 -split ':') | ForEach-Object {
[Convert]::ToByte($_,16) }
$base64 = [Convert]::ToBase64String($bytes)
$urlsafe = $base64.Replace('+','-').Replace('/','_').TrimEnd('=')
$urlsaf
```

## 5. UI Theming

### 5.1. res/values/colors.xml

```xml
<resources>
<!-- Primary brand color for buttons/CTAs -->
<color name="authui_primary">#0057D9</color>
<color name="authui_onPrimary">#FFFFFF</color>
<!-- Background & text colors for the auth screens -->
<color name="authui_background">#FFFFFFFF</color>
<color name="authui_onBackground">#FF111318</color>
<!-- Error colors for validation/messages -->
<color name="authui_error">#FFB3261E</color>
<color name="authui_onError">#FFFFFFFF</color>
<!-- Link color (e.g., “Forgot password?”, “Sign up”) -->
<color name="authui_link">#FF1A73E8</color>
</resources>
```

### 5.2. res/values/themes.xml

```xml
<style name="Theme.Splash" parent="Theme.Material3.Light.NoActionBar">
<item name="lu_colorGoogleButton">@color/authui_primary</item>
<item name="lu_colorFacebookButton">@color/authui_primary</item>
<item name="lu_colorMicrosoftButton">@color/authui_primary</item>
<item name="lu_colorLink">@color/authui_link</item>
<item name="lu_colorLoginButton">@color/authui_primary</item>
</style>
```

## 6. Layout

Create a container for auth fragments (res/layout/activity_main.xml):

```xml
<FrameLayout
xmlns:android="http://schemas.android.com/apk/res/android"
android:id="@+id/auth_container"
android:layout_width="match_parent"
android:layout_height="match_parent"/>
```

## 7. App Initialization

### 7.1. Initialize

```kotlin
LoginUi.init(
applicationContext,
"http://10.0.2.2:3000/", // your backend base URL
SocialConfig(
showGoogle = true,
showFacebook = false, // not supported
showMicrosoft = true
)
)
```

### 7.2. Show Login UI

```kotlin
supportFragmentManager.beginTransaction()
.replace(R.id.auth_container, com.example.loginui.LoginFragment())
.commit()
```

### 7.3. Listen for Results

Use FragmentResultListener to handle login/signup outcomes.

```text
● ACTION_LOGIN_SUCCESS → read ACCESS_TOKEN + REFRESH_TOKEN
● ACTION_SIGNUP → show SignupFragment
● ACTION_MICROSOFT → start Microsoft sign-in flow
● ACTION_GOOGLE → trigger Google OAuth
```

```kotlin
supportFragmentManager.setFragmentResultListener(LoginFragment.RESULT_KEY,
this) { _, bundle ->
when (bundle.getString(LoginFragment.ACTION)) {
LoginFragment.ACTION_LOGIN_SUCCESS -> {
val access = bundle.getString(LoginFragment.ACCESS_TOKEN) ?: ""
val refresh = bundle.getString(LoginFragment.REFRESH_TOKEN) ?: ""
Toast.makeText(this, "AccessToken:\n$access",
Toast.LENGTH_LONG).show()
Toast.makeText(this, "RefreshToken:\n$refresh",
Toast.LENGTH_LONG).show()
// TODO: store tokens in your app if desired
}
LoginFragment.ACTION_LOGIN_ERROR -> {
val code = bundle.getInt(LoginFragment.STATUS_CODE, -1)
val err = bundle.getString(LoginFragment.ERROR) ?: "Unknown error"
Toast.makeText(this, "Login failed ($code): $err",
Toast.LENGTH_LONG).show()
}
LoginFragment.ACTION_SIGNUP -> {
supportFragmentManager.commit {
replace(R.id.fragment_container, SignupFragment())
addToBackStack("signup")
}
}
LoginFragment.ACTION_MICROSOFT -> {
// We'll wire this up to MSAL next step
startMicrosoftSignIn()
}
LoginFragment.ACTION_GOOGLE -> {
val base = RetrofitInstance.retrofit.baseUrl().toString()
GoogleOAuth.start(this, base)
}
}
}
supportFragmentManager.setFragmentResultListener(SignupFragment.RESULT_KEY,
this) { _, bundle ->
when (bundle.getString(SignupFragment.ACTION)) {
SignupFragment.ACTION_SIGNUP_SUCCESS -> {
val email = bundle.getString(SignupFragment.EMAIL) ?: ""
Toast.makeText(this, "Signed up: $email", Toast.LENGTH_LONG).show()
// Optionally navigate back to Login
supportFragmentManager.popBackStack()
}
SignupFragment.ACTION_SIGNUP_ERROR -> {
val code = bundle.getInt(SignupFragment.STATUS_CODE, -1)
val err = bundle.getString(SignupFragment.ERROR) ?: "Unknown error"
Toast.makeText(this, "Signup failed ($code): $err",
Toast.LENGTH_LONG).show()
}
SignupFragment.ACTION_GO_LOGIN -> {
supportFragmentManager.popBackStack()
}
}
}
```

### 7.4. Jetpack Compose Integration (Optional)

If your app uses Jetpack Compose instead of XML-based layouts, you can embed the
LoginFragment and SignupFragment into Compose with AndroidView and
FragmentContainerView.

In app/build.gradle dependencies Add:

```gradle
implementation("androidx.fragment:fragment-ktx:1.8.4")
```

Example MainActivity setup:

```kotlin
class MainActivity : FragmentActivity() {
private val currentScreen = mutableStateOf("login")
private var msalApp: IPublicClientApplication? = null
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
// Initialize LoginUi
LoginUi.init(
applicationContext,
"http://localhost:3000", // Local backend
SocialConfig(showGoogle = true, showFacebook = false,
showMicrosoft = true)
)
// Compose UI
setContent {
MaterialTheme {
when (currentScreen.value) {
"login" -> LoginFragmentHost()
"signup" -> SignupFragmentHost()
}
}
}
}
}
```

Helper Composables to host fragments:

```kotlin
// Compose Hosts For Login Fragment
@SuppressLint("ContextCastToActivity")
@Composable
fun LoginFragmentHost(modifier: Modifier = Modifier) {
val activity = LocalContext.current as FragmentActivity
val fm = activity.supportFragmentManager
val containerId = remember { View.generateViewId() }
AndroidView(
modifier = modifier.fillMaxSize(),
factory = { ctx ->
FragmentContainerView(ctx).apply { id = containerId }
},
update = {
if (fm.findFragmentById(containerId) == null) {
fm.commit { replace(containerId, LoginFragment()) }
}
}
)
}
```

For the sign up fragment host replace LoginFragment()with SignupFragment().

## 8. Sign-In Options

Configure via SocialConfig:

```kotlin
SocialConfig(
showGoogle = true, // fixed redirect
showFacebook = false,// not supported yet
showMicrosoft = true // requires MSAL setup
)
```

## 9. Microsoft Sign-In Flow

1. Initialize MSAL

```kotlin
PublicClientApplication.create(
applicationContext,
R.raw.msal_config,
object : IPublicClientApplication.ApplicationCreatedListener {
override fun onCreated(app: IPublicClientApplication) {
msalApp = app }
override fun onError(e: MsalException) { /* handle */ }
}
)
```

2. Start sign-in

```kotlin
private fun startMicrosoftSignIn() {
val app = msalApp ?: return
val params = AcquireTokenParameters.Builder()
.startAuthorizationFromActivity(this)
.withScopes(listOf("openid", "profile", "email"))
.withCallback(object : AuthenticationCallback {
override fun onSuccess(result: IAuthenticationResult) {
val idToken = result.account?.idToken
Toast.makeText(this@MainActivity, "Microsoft login
success", Toast.LENGTH_SHORT).show()
}
override fun onError(e: MsalException) {
Toast.makeText(this@MainActivity, "Microsoft login
error: ${e.message}", Toast.LENGTH_SHORT).show()
Log.e("MSAL_ERROR", "MSAL Exception", e)
}
override fun onCancel() {
Toast.makeText(this@MainActivity, "Microsoft login
canceled", Toast.LENGTH_SHORT).show()
}
})
.build()
app.acquireToken(params)
}
```

## 10. Google Sign-In Flow

```text
● Triggered automatically via ACTION_GOOGLE.
● Redirect is fixed: restosoft://auth/google/callback.
```

```kotlin
private fun startGoogleSignIn() {
val baseUrl = "http://localhost:3000" // must match LoginUi.init()
GoogleOAuth.start(this, baseUrl)
}
```

Handle deep link in MainActivity:

```kotlin
override fun onNewIntent(intent: Intent) {
super.onNewIntent(intent)
setIntent(intent)
handleGoogleDeepLink(intent)
}
```

```kotlin
private fun handleGoogleDeepLink(intent: Intent?) {
val tokens = GoogleOAuth.parseFromUri(intent?.data) ?: return
val accessToken = tokens.accessToken
val refreshToken = tokens.refreshToken
// store securely
}
```

## 11. User Profile Integration

1. dependencies

```gradle
// Material Components (defines TextInputLayout, MaterialCardView, etc.)
implementation("com.google.android.material:material:1.12.0")
// ConstraintLayout (defines layoutDescription for MotionLayout)
implementation("androidx.constraintlayout:constraintlayout:2.2.0")
// Optional but often needed for MaterialCardView compatibility
implementation("androidx.cardview:cardview:1.0.0")
// --- OkHttp (network layer) ---
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
// --- Retrofit (REST client) ---
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
// --- Moshi (JSON parser) ---
implementation("com.squareup.moshi:moshi:1.15.1")
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
```

2. Create TokenStore file under com.example.yourapp/util

```kotlin
import android.content.Context
object TokenStore {
private const val PREF = "auth_prefs"
private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"
private fun prefs(ctx: Context) =
ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
fun setTokens(ctx: Context, access: String?, refresh: String?) {
prefs(ctx).edit()
.putString(KEY_ACCESS, access)
.putString(KEY_REFRESH, refresh)
.apply()
}
fun clear(ctx: Context) {
prefs(ctx).edit().clear().apply()
}
fun access(ctx: Context): String? = prefs(ctx).getString(KEY_ACCESS, null)
fun refresh(ctx: Context): String? = prefs(ctx).getString(KEY_REFRESH,
null)
}
```

3. LoginUi.init in mainActivity

```kotlin
LoginUi.init(
applicationContext,
"http://localhost:3000", // Local backend
SocialConfig(showGoogle = true, showFacebook = false, showMicrosoft =
true),
tokenProvider = { TokenStore.access(applicationContext) },
onLogout = {
TokenStore.clear(applicationContext)
},
onTokenChanged = { newToken ->
TokenStore.setTokens(this@MainActivity, newToken,
TokenStore.refresh(this@MainActivity))
updateAuthItemLabel()
}
)
LoginUi.setRefreshBearer(TokenStore.refresh(this))
LoginUi.notifyTokenChanged(TokenStore.access(this))
```

4. wherever there is a successful login:

```kotlin
TokenStore.setTokens(this, tokens.accessToken, tokens.refreshToken)
LoginUi.setRefreshBearer(tokens.refreshToken)
LoginUi.notifyTokenChanged(tokens.accessToken)
```

5. Profile fragment and logout action

```kotlin
supportFragmentManager.setFragmentResultListener(
com.example.loginui.ProfileFragment.RESULT_KEY, this
) { _, bundle ->
when (bundle.getString("action")) {
com.example.loginui.ProfileFragment.ACTION_LOGOUT -> {
selectedItemId = R.id.homeItem
updateSelection(selectedItemId)
supportFragmentManager.popBackStack(
null,
androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
)
supportFragmentManager.commit {
replace(R.id.fragment_container, HomeFragment())
}
}
}
}
```

## 12. Backend Base URL

```text
● Local emulator: http://10.0.2.2:3000/
● With adb reverse: http://localhost:3000/
● Production: HTTPS endpoint
```

## 13. Token Handling

```text
● Store tokens from LoginFragment, GoogleOAuth, or MSAL exchange.
● Use EncryptedSharedPreferences or equivalent.
```

## 14. Troubleshooting Checklist

```text
✅ Google redirect intent-filter correct
✅ MSAL redirect_uri matches Azure app + manifest
✅ authority_url = .../consumers
✅ All dependencies added
✅ Base URL correct + reachable
✅ Container = R.id.auth_container
✅ androidx.browser included
```

## 15. Emulator ↔ Host Setup

If Google sign-in fails against localhost, use ADB reverse.

Windows (PowerShell):

```text
adb devices
adb -s emulator-5554 reverse tcp:3000 tcp:3000
```

macOS/Linux:

```text
adb devices
adb -s emulator-5554 reverse tcp:3000 tcp:3000
```
