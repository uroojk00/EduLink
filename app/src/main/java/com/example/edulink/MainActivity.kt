package com.example.edulink
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext  // Needed to get context for file access
// --- ICON IMPORTS ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
// AutoMirrored Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LiveHelp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Person
// --- Other Imports ---
import androidx.compose.material3.* // Includes HorizontalDivider and core M3 Composables
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.Response
import retrofit2.HttpException
import java.io.IOException
// Imports for File Upload (Retrofit Multipart)
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import java.io.File
import okio.BufferedSink // For custom RequestBody
import okio.Source // For custom RequestBody
import okio.source // For custom RequestBody
import android.content.ContentResolver
import android.provider.OpenableColumns
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
// REQUIRED IMPORT for experimental Material 3 APIs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn // For setting max height in LazyColumn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow

// ********************************************************************
// ********** 1. NETWORK DATA CLASSES (MODELS) ************************
// ********************************************************************
// --- Data class for User Profile (used in /api/users/me) ---
data class UserProfile(
    @SerializedName("_id") val id: String,
    val name: String,
    val email: String,
    val phoneNumber: String?,
    val bio: String?,
    val institution: String?,
    val notificationsEnabled: Boolean,
    val uploadedResources: List<ResourceHeader> = emptyList(),
    val bookmarkedResources: List<ResourceHeader> = emptyList()
)
// ResourceHeader used for lists of resources in the profile
data class ResourceHeader(
    @SerializedName("_id") val id: String,
    val title: String,
    val type: String = "PDF" // Added type for display
)
// --- Data class for Resource Upload Metadata ---
data class ResourceUploadMetadata(
    val title: String,
    val description: String,
    val subject: String,
    val category: String
)
data class UploadResponse(
    val message: String,
    val resourceId: String
)
// --- Auth Data Classes (kept for context) ---
data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val phoneNumber: String?,
    val notificationsEnabled: Boolean = true
)
data class LoginRequest(
    @SerializedName("email")
    val emailOrPhone: String,
    val password: String
)
data class AuthResponse(
    val token: String,
    val user: BasicUser
)
data class BasicUser(
    val id: String,
    val name: String,
    val email: String
)

// ********************************************************************
// ********** 2. RETROFIT SETUP AND API INTERFACE *********************
// ********************************************************************
interface EduLinkApi {
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    @GET("users/me")
    suspend fun getMyProfile(@Header("x-auth-token") token: String): Response<UserProfile>
    // API endpoint for file upload (Multipart)
    @Multipart
    @POST("resources/upload") // Example route
    suspend fun uploadResource(
        @Header("x-auth-token") token: String,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part // The actual file part
    ): Response<UploadResponse>
}

object RetrofitClient {
    // IMPORTANT: Please update this IP address (172.16.63.176) to the actual IP where your backend
    // server is running.
    internal const val BASE_URL = "http://192.168.1.37:5000/api/"
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val apiService: EduLinkApi by lazy {
        retrofit.create(EduLinkApi::class.java)
    }
}

// ********************************************************************
// ********** 3. FILE UPLOAD UTILITY CLASSES **************************
// ********************************************************************
// Custom RequestBody to stream data directly from a URI via ContentResolver.
class UriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: String
) : RequestBody() {
    override fun contentType() = contentType.toMediaTypeOrNull()
    // Required by RequestBody. Streams the data from the URI to the OkHttp sink.
    override fun writeTo(sink: BufferedSink) {
        var source: Source? = null
        try {
            // Open the InputStream and wrap it with Okio Source
            val inputStream = contentResolver.openInputStream(uri)
            source = inputStream?.source() ?: throw IOException("Could not open file stream for URI: $uri")
            // Read source into sink
            sink.writeAll(source)
        } finally {
            source?.close()
        }
    }
}
// Helper function for safely getting the file name from a URI
@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
    }
    // Fallback for file scheme URIs (less common but safer to include)
    return uri.path?.let { File(it).name }
}

// ********************************************************************
// ********** 4. AUTH AND RESOURCE VIEW MODELS ************************
// ********************************************************************
// --- Auth ViewModel (UPDATED FOR VALIDATION MESSAGES) ---
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val validationMessage: String? = null, // NEW: For local validation errors
    val isAuthenticated: Boolean = false,
    val token: String? = null
)
class AuthViewModel : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    // Function to set local validation error
    fun setValidationMessage(message: String?) {
        // Only set validation message, clear server error
        uiState = uiState.copy(validationMessage = message, errorMessage = null)
    }

    // Function to clear all messages
    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, validationMessage = null)
    }

    private fun updateState(
        isLoading: Boolean = false,
        errorMessage: String? = null,
        validationMessage: String? = uiState.validationMessage, // Ensure validation message persists if not explicitly cleared
        isAuthenticated: Boolean = uiState.isAuthenticated,
        token: String? = uiState.token
    ) {
        uiState = AuthUiState(
            isLoading = isLoading,
            errorMessage = errorMessage,
            validationMessage = validationMessage,
            isAuthenticated = isAuthenticated,
            token = token
        )
    }

    fun login(request: LoginRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            updateState(isLoading = true, errorMessage = null, validationMessage = null)
            try {
                val response = RetrofitClient.apiService.login(request)
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    updateState(isAuthenticated = true, token = authResponse.token)
                    onSuccess()
                } else {
                    val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "Invalid credentials or API error"
                    updateState(errorMessage = errorMsg.substringAfterLast("msg\":").trimEnd('}', '"'))
                }
            } catch (e: HttpException) {
                updateState(errorMessage = "Server Error: ${e.code()}")
            } catch (e: IOException) {
                updateState(errorMessage = "Connection Error. Is the Node.js server running and reachable at ${RetrofitClient.BASE_URL}?")
            } catch (e: Exception) {
                updateState(errorMessage = "An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun signup(request: SignupRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            updateState(isLoading = true, errorMessage = null, validationMessage = null)
            try {
                val response = RetrofitClient.apiService.signup(request)
                if (response.isSuccessful) {
                    updateState()
                    onSuccess()
                } else {
                    val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "Signup failed. Please check details."
                    updateState(errorMessage = errorMsg.substringAfterLast("msg\":").trimEnd('}', '"'))
                }
            } catch (e: Exception) {
                updateState(errorMessage = "Connection Error or Server issue during signup.")
            }
        }
    }

    fun logout() {
        uiState = AuthUiState(isAuthenticated = false, token = null)
    }
}

// --- Resource ViewModel (UPDATED FOR ERROR PARSING) ---
data class ResourceUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val uploadSuccess: String? = null
)
class ResourceViewModel(private val authViewModel: AuthViewModel) : ViewModel() {
    var uiState by mutableStateOf(ResourceUiState())
        private set

    fun fetchProfile() {
        val token = authViewModel.uiState.token
        if (token == null) {
            uiState = uiState.copy(errorMessage = "Authentication token is missing.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                val response = RetrofitClient.apiService.getMyProfile(token)
                if (response.isSuccessful && response.body() != null) {
                    // Simulate adding resource types for display simplicity
                    val profileData = response.body()!!.copy(
                        uploadedResources = response.body()!!.uploadedResources.mapIndexed { index, header ->
                            header.copy(type = if (index % 3 == 0) "PDF" else if (index % 3 == 1) "DOCX" else "PPT")
                        },
                        bookmarkedResources = response.body()!!.bookmarkedResources.mapIndexed { index, header ->
                            header.copy(type = if (index % 2 == 0) "VIDEO" else "NOTES")
                        }
                    )
                    uiState = uiState.copy(profile = profileData, isLoading = false)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to fetch profile."
                    uiState = uiState.copy(errorMessage = errorMsg, isLoading = false)
                }
            } catch (e: Exception) {
                uiState =
                    uiState.copy(errorMessage = "Network error: ${e.message}", isLoading = false)
            }
        }
    }

    // Function to handle the file upload, now accepting Uri and Context
    fun uploadResource(
        metadata: ResourceUploadMetadata,
        fileUri: Uri, // Changed to accept Uri
        context: Context, // Added Context to access ContentResolver
        onSuccess: () -> Unit
    ) {
        val token = authViewModel.uiState.token
        if (token == null) {
            uiState = uiState.copy(errorMessage = "Authentication token is missing.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null, uploadSuccess = null)
            try {
                // 1. Create RequestBody for metadata fields
                val titlePart = metadata.title.trim().toRequestBody("text/plain".toMediaTypeOrNull())
                val descriptionPart =
                    metadata.description.trim().toRequestBody("text/plain".toMediaTypeOrNull())
                val subjectPart = metadata.subject.trim().toRequestBody("text/plain".toMediaTypeOrNull())
                val categoryPart = metadata.category.trim().toRequestBody("text/plain".toMediaTypeOrNull())

                // 2. Determine file metadata and create MultipartBody.Part for the file
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
                val fileName = getFileName(context, fileUri) ?: "uploaded_resource_file"
                // Use the custom UriRequestBody to stream the file content
                val fileRequestBody = UriRequestBody(contentResolver, fileUri, mimeType)
                val filePart = MultipartBody.Part.createFormData(
                    "file", // MUST match the field name expected by the server on the backend
                    fileName,
                    fileRequestBody
                )

                val response = RetrofitClient.apiService.uploadResource(
                    token = token,
                    title = titlePart,
                    description = descriptionPart,
                    subject = subjectPart,
                    category = categoryPart,
                    file = filePart
                )

                if (response.isSuccessful && response.body() != null) {
                    uiState = uiState.copy(
                        isLoading = false,
                        uploadSuccess = "Resource uploaded successfully! Filename: $fileName"
                    )
                    onSuccess()
                } else {
                    // --- REINFORCED ERROR PARSING LOGIC (Handles clean text and HTML) ---
                    val rawErrorBody = response.errorBody()?.string()
                    val detailedError = if (rawErrorBody != null) {

                        // 1. Try to extract clean error message from <pre> tags (for HTML error pages)
                        val preTagContent = rawErrorBody
                            .substringAfter("<pre>", "")
                            .substringBefore("</pre>", "")
                            .trim()
                            .replace("&quot;", "\"") // Decode HTML entities

                        // 2. If <pre> is empty, use the raw body, which will contain the clean validation error text
                        if (preTagContent.isNotBlank()) {
                            preTagContent
                        } else {
                            // Use raw body, truncating for cleaner display if it's unexpected content
                            rawErrorBody.take(250).trim().takeIf { it.isNotBlank() }
                                ?: "Unknown server error. Status: ${response.code()}"
                        }
                    } else {
                        "Upload failed due to server error. Status: ${response.code()}"
                    }

                    // Display the extracted, detailed error message
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Server Upload Failed: $detailedError"
                    )
                }
            } catch (e: IOException) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "File I/O or Network error: ${e.message}"
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "An unexpected error occurred during upload: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, uploadSuccess = null)
    }
}

// ********************************************************************
// ********** 5. MAIN ACTIVITY AND COMPOSABLES (UI) *******************
// ********************************************************************
// --- Navigation Destinations ---
object EduLinkDestinations {
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    const val HOME_ROUTE = "home"
    const val PROFILE_ROUTE = "profile"
    const val NOTIFICATIONS_ROUTE = "notifications"
    const val RESOURCE_DETAILS_ROUTE = "resource_details/{resourceId}"
    const val FORGOT_PASSWORD_ROUTE = "forgot_password"
    const val SETTINGS_ROUTE = "settings"
    const val HELP_ROUTE = "help_support"
}
// --- Bottom Navigation ---
sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Resources : BottomNavItem("resources", Icons.Default.Home, "Resources")
    object Post : BottomNavItem("post", Icons.Default.Upload, "Upload")
    object Discussion : BottomNavItem("discussion", Icons.Default.Forum, "Forum")
    object News : BottomNavItem("news", Icons.Default.Newspaper, "News")
}
// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EduLinkApp()
                }
            }
        }
    }
}

@Composable
fun EduLinkApp() {
    val mainNavController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    // ResourceViewModel depends on AuthViewModel for the token
    val resourceViewModel: ResourceViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ResourceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ResourceViewModel(authViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    })
    AppNavHost(
        mainNavController = mainNavController,
        authViewModel = authViewModel,
        resourceViewModel = resourceViewModel
    )
}

@Composable
fun AppNavHost(
    mainNavController: NavHostController,
    authViewModel: AuthViewModel,
    resourceViewModel: ResourceViewModel
) {
    val startDestination = if (authViewModel.uiState.isAuthenticated) EduLinkDestinations.HOME_ROUTE else EduLinkDestinations.LOGIN_ROUTE
    NavHost(
        navController = mainNavController,
        startDestination = startDestination
    ) {
        // --- Authentication Flow ---
        composable(EduLinkDestinations.LOGIN_ROUTE) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    mainNavController.navigate(EduLinkDestinations.HOME_ROUTE) {
                        popUpTo(EduLinkDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }  ,
                onNavigateToSignup = { mainNavController.navigate(EduLinkDestinations.SIGNUP_ROUTE) }  ,
                onForgotPassword = { mainNavController.navigate(EduLinkDestinations.FORGOT_PASSWORD_ROUTE) }
            )
        }
        composable(EduLinkDestinations.SIGNUP_ROUTE) {
            SignupScreen(
                viewModel = authViewModel,
                onSignupComplete = {
                    mainNavController.navigate(EduLinkDestinations.LOGIN_ROUTE) {
                        popUpTo(EduLinkDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(EduLinkDestinations.FORGOT_PASSWORD_ROUTE) {
            ForgotPasswordScreen(onBack = mainNavController::popBackStack)
        }
        // --- Main App Flow ---
        composable(EduLinkDestinations.HOME_ROUTE) {
            MainScreen(mainNavController = mainNavController, authViewModel = authViewModel, resourceViewModel = resourceViewModel)
        }
        composable(EduLinkDestinations.PROFILE_ROUTE) {
            ProfileScreen(
                onBack = mainNavController::popBackStack,
                resourceViewModel = resourceViewModel,
                onResourceClick = { resourceId ->
                    mainNavController.navigate("resource_details/$resourceId")
                }
            )
        }
        composable(EduLinkDestinations.NOTIFICATIONS_ROUTE) {
            NotificationScreen(onBack = mainNavController::popBackStack)
        }
        composable(EduLinkDestinations.SETTINGS_ROUTE) {
            SettingsScreen(onBack = mainNavController::popBackStack)
        }
        composable(EduLinkDestinations.HELP_ROUTE) {
            HelpSupportScreen(onBack = mainNavController::popBackStack)
        }
        composable(EduLinkDestinations.RESOURCE_DETAILS_ROUTE) { backStackEntry ->
            val resourceId = backStackEntry.arguments?.getString("resourceId") ?: "N/A"
            ResourceDetailsScreen(resourceId = resourceId, onBack = mainNavController::popBackStack)
        }
    }
}
// --- Authentication Screens (implementations for completeness) ---
@Composable
fun LoginScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit, onNavigateToSignup: () -> Unit, onForgotPassword: () -> Unit) {
    val uiState = viewModel.uiState
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAuthenticated) { if (uiState.isAuthenticated) { onLoginSuccess() } }

    // NEW: Validation flags/state
    val isFormValid = emailOrPhone.isNotBlank() && password.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("EduLink Login", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 32.dp))

        OutlinedTextField(
            value = emailOrPhone,
            onValueChange = {
                emailOrPhone = it
                viewModel.clearMessages()
            },
            label = { Text("Email or Phone") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = !uiState.isLoading,
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearMessages()
            },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            enabled = !uiState.isLoading,
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible } ) {
                    Icon(imageVector = image, contentDescription = "Toggle password visibility")
                }
            }
        )

        // Display validation/error message
        if (uiState.errorMessage != null) { Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        if (uiState.validationMessage != null) { Text(uiState.validationMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }

        Button(
            onClick = {
                val request = LoginRequest(emailOrPhone = emailOrPhone, password = password)
                viewModel.login(request, onLoginSuccess)
            } ,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !uiState.isLoading && isFormValid
        ) {
            if (uiState.isLoading) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } else { Text("Login") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onForgotPassword, enabled = !uiState.isLoading) { Text("Forgot Password?") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToSignup, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Create New Account") }
    }
}

@Composable
fun SignupScreen(viewModel: AuthViewModel, onSignupComplete: () -> Unit) {
    val uiState = viewModel.uiState;
    var name by remember { mutableStateOf("") };
    var email by remember { mutableStateOf("") };
    var phone by remember { mutableStateOf("") };
    var password by remember { mutableStateOf("") };
    var notificationsEnabled by remember { mutableStateOf(true) }

    // Validation flags
    val isFormValid = name.isNotBlank() && email.isNotBlank() && password.isNotBlank()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Sign Up", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 32.dp)) }
        item { OutlinedTextField(value = name, onValueChange = { name = it; viewModel.clearMessages() }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = !uiState.isLoading) }
        item { OutlinedTextField(value = email, onValueChange = { email = it; viewModel.clearMessages() }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = !uiState.isLoading) }
        item { OutlinedTextField(
            value = phone.filter { it.isDigit() }.take(10), // Enforce 10 digits in input
            onValueChange = {
                phone = it.filter { it.isDigit() }.take(10) // Update state with filtered value
                viewModel.clearMessages()
            },
            label = { Text("Phone Number (10 digits)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            enabled = !uiState.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        ) }
        item { OutlinedTextField(value = password, onValueChange = { password = it; viewModel.clearMessages() }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), enabled = !uiState.isLoading) }

        item { Text("OTP verification placeholder (Sent to Email/Phone)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp)) }
        item { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.padding(end = 8.dp)); Text("Enable Notifications?", modifier = Modifier.weight(1f)); Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it }, enabled = !uiState.isLoading) } }

        // Display validation/error message
        item {
            if (uiState.errorMessage != null) {
                Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (uiState.validationMessage != null) {
                Text(uiState.validationMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        item {
            Button(
                onClick = {
                    // --- Client-Side Validation ---
                    val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

                    if (!email.matches(emailRegex) || !email.endsWith(".com")) {
                        viewModel.setValidationMessage("Input a **valid gmail ID** (must end with .com and contain @).")
                        return@Button
                    }
                    if (phone.isNotBlank() && phone.length != 10) {
                        viewModel.setValidationMessage("Input a **valid 10-digit phone number**.")
                        return@Button
                    }

                    viewModel.clearMessages() // Clear local message if validation passes

                    val request = SignupRequest(name, email, password, phone.takeIf { it.isNotBlank() }, notificationsEnabled)
                    viewModel.signup(request) { onSignupComplete() }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 16.dp),
                enabled = isFormValid && !uiState.isLoading // Enable button if basic fields are filled and not loading
            ) {
                if (uiState.isLoading) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) } else { Text("Complete Sign Up") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    var emailOrPhone by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Forgot Password") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) } ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Enter your Email or Phone to receive a reset link/code.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 24.dp))
            OutlinedTextField(value = emailOrPhone, onValueChange = { emailOrPhone = it }, label = { Text("Email or Phone") }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
            Button(onClick = { /* Send reset link/code logic */ }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Send Reset Request") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailsScreen(resourceId: String, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Resource: $resourceId") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Details for Resource ID: $resourceId")
        }
    }
}

@Composable
fun ProfileDetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ResourceListSection(title: String, resources: List<ResourceHeader>, onResourceClick: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            HorizontalDivider(Modifier.padding(bottom = 8.dp))
            if (resources.isEmpty()) {
                Text("No resources found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                resources.take(5).forEachIndexed { index, resource ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onResourceClick(resource.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (resource.type) { "PDF" -> Icons.Default.PictureAsPdf; "DOCX" -> Icons.Default.Description; "PPT" -> Icons.Default.Attachment; "VIDEO" -> Icons.Default.Attachment; "NOTES" -> Icons.Default.Description; else -> Icons.Default.Attachment }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(resource.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "View", modifier = Modifier.size(16.dp).rotate(180f))
                    }
                    if (index < resources.size - 1 && index < 4) { HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)) }
                }
                if (resources.size > 5) {
                    TextButton(onClick = { /* Navigate to full list */ }, modifier = Modifier.align(Alignment.End)) {
                        Text("View all (${resources.size})")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, resourceViewModel: ResourceViewModel, onResourceClick: (String) -> Unit) {
    val uiState = resourceViewModel.uiState
    // Fetch profile data when the screen is first composed
    LaunchedEffect(Unit) {
        resourceViewModel.fetchProfile()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(Modifier.padding(32.dp));
                    Text("Loading profile...")
                }
                uiState.errorMessage != null -> {
                    Text("Error: ${uiState.errorMessage}", color = MaterialTheme.colorScheme.error);
                    Button(onClick = { resourceViewModel.fetchProfile() } ) {
                        Text("Retry")
                    }
                }
                uiState.profile != null -> {
                    val profile = uiState.profile
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            // User Name/Username
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            ProfileDetailRow(Icons.Default.Attachment, "Email", profile.email)
                            ProfileDetailRow(Icons.Default.Attachment, "Phone", profile.phoneNumber ?: "N/A")
                            ProfileDetailRow(Icons.Default.Info, "Institution", profile.institution ?: "Not set")
                            ProfileDetailRow(Icons.Default.Description, "Bio", profile.bio ?: "Tell us about yourself...")
                        }
                    }
                    // Resources Uploaded
                    ResourceListSection(
                        title = "My Uploaded Resources (${profile.uploadedResources.size})",
                        resources = profile.uploadedResources,
                        onResourceClick = onResourceClick
                    )
                    Spacer(Modifier.height(16.dp))
                    // Resources Bookmarked
                    ResourceListSection(
                        title = "Bookmarked Resources (${profile.bookmarkedResources.size})",
                        resources = profile.bookmarkedResources,
                        onResourceClick = onResourceClick
                    )
                }
            }
        }
    }
}

// --- UPDATED POST SCREEN (Real File Picker) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(resourceViewModel: ResourceViewModel) {
    val uiState = resourceViewModel.uiState
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Lecture Notes") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val isFormValid = title.isNotBlank() && description.isNotBlank() && selectedFileUri != null
    // Get Context for ContentResolver access
    val context = LocalContext.current
    // Activity Result Launcher for picking any content type (e.g., PDF, DOCX, image)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            // Get the display name from the ContentResolver
            selectedFileName = getFileName(context, uri) ?: uri.lastPathSegment ?: "Selected File"
            resourceViewModel.clearMessages()
        } else {
            selectedFileUri = null
            selectedFileName = ""
        }
    }
    // Reset messages when screen is viewed
    DisposableEffect(Unit) {
        onDispose { resourceViewModel.clearMessages() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Upload New Resource",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Metadata Fields
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Resource Title") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            enabled = !uiState.isLoading
        )
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(bottom = 12.dp),
            enabled = !uiState.isLoading
        )
        OutlinedTextField(
            value = subject, onValueChange = { subject = it },
            label = { Text("Subject/Course") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            enabled = !uiState.isLoading
        )
        // Dropdown for Category (simulated for simplicity)
        OutlinedTextField(
            value = category,
            onValueChange = { /* Handle change if using a real dropdown */ },
            label = { Text("Category") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            readOnly = true,
            enabled = !uiState.isLoading
        )

        // File Picker/Uploader
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = { filePickerLauncher.launch("*/*") } , // Launch file picker
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Select File to Upload", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        selectedFileName.ifEmpty { "Tap to choose file (PDF, DOCX, etc.)" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Error/Success Message Display
        uiState.errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        uiState.uploadSuccess?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Upload Button
        Button(
            onClick = {
                if (selectedFileUri != null) {
                    val metadata = ResourceUploadMetadata(title, description, subject, category)
                    // Call ViewModel with the actual Uri and Context
                    resourceViewModel.uploadResource(metadata, selectedFileUri!!, context) {
                        // Clear form fields on successful upload
                        title = ""
                        description = ""
                        subject = ""
                        selectedFileUri = null
                        selectedFileName = ""
                    }
                }
            }  ,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = isFormValid && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Start Upload")
            }
        }
    }
}
// --- Main Screen Structure (Updated to include PostScreen) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainNavController: NavHostController, authViewModel: AuthViewModel, resourceViewModel: ResourceViewModel) {
    val bottomNavController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                DrawerHeader()
                NavigationDrawerItem(
                    label = { Text("Profile") },
                    selected = false,
                    onClick = {
                        mainNavController.navigate(EduLinkDestinations.PROFILE_ROUTE)
                        scope.launch { drawerState.close() }
                    }  ,
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )
                NavigationDrawerItem(label = { Text("My Uploads") }, selected = false, onClick = { mainNavController.navigate(EduLinkDestinations.PROFILE_ROUTE); scope.launch { drawerState.close() } } , icon = { Icon(Icons.Default.Upload, contentDescription = null) } )
                NavigationDrawerItem(label = { Text("My Bookmarks") }, selected = false, onClick = { mainNavController.navigate(EduLinkDestinations.PROFILE_ROUTE); scope.launch { drawerState.close() } } , icon = { Icon(Icons.Default.Star, contentDescription = null) } )
                NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { mainNavController.navigate(EduLinkDestinations.SETTINGS_ROUTE); scope.launch { drawerState.close() } } , icon = { Icon(Icons.Default.Settings, contentDescription = null) } )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(label = { Text("Help & Support") }, selected = false, onClick = { mainNavController.navigate(EduLinkDestinations.HELP_ROUTE); scope.launch { drawerState.close() } } , icon = { Icon(Icons.AutoMirrored.Filled.LiveHelp, contentDescription = null) } )
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    selected = false,
                    onClick = {
                        authViewModel.logout()
                        mainNavController.navigate(EduLinkDestinations.LOGIN_ROUTE) {
                            popUpTo(mainNavController.graph.id) { inclusive = true }
                        }
                        scope.launch { drawerState.close() }
                    }  ,
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = { EduLinkTopBar(onMenuClick = { scope.launch { drawerState.open() } } , onNotificationClick = { mainNavController.navigate(EduLinkDestinations.NOTIFICATIONS_ROUTE) } ) } ,
            bottomBar = { EduLinkBottomBar(bottomNavController) }
        ) { paddingValues ->
            // Content based on bottom navigation
            NavHost(
                navController = bottomNavController,
                startDestination = BottomNavItem.Resources.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable (BottomNavItem.Resources.route) { PlaceholderScreen(BottomNavItem.Resources.label) }
                composable (BottomNavItem.Post.route) { PostScreen(resourceViewModel) } // NEW POST SCREEN
                composable (BottomNavItem.Discussion.route) { PlaceholderScreen(BottomNavItem.Discussion.label) }
                composable (BottomNavItem.News.route) { PlaceholderScreen(BottomNavItem.News.label) }
            }
        }
    }
}
// --- Utility Composables (implementations for completeness) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Notifications") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) } ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Notifications List (Icon functional via TopBar)") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) } ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Settings Screen (Icon functional via Drawer)") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Help & Support") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) } ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Help/Support Screen (Icon functional via Drawer)") }
    }
}

@Composable
fun DrawerHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Info, contentDescription = "App Info", modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary);
        Spacer(Modifier.height(8.dp));
        Text("EduLink", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold);
        Text("Study & Connect", style = MaterialTheme.typography.bodySmall);
        HorizontalDivider(Modifier.padding(top = 16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EduLinkTopBar(onMenuClick: () -> Unit, onNotificationClick: () -> Unit) {
    TopAppBar(title = { Text("EduLink") }, navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, contentDescription = "Menu") } } , actions = { IconButton(onClick = { /* Search functionality */ } ) { Icon(Icons.Default.Search, contentDescription = "Search") } ; IconButton(onClick = onNotificationClick) { Icon(Icons.Default.Notifications, contentDescription = "Notifications") } } )
}

@Composable
fun EduLinkBottomBar(navController: NavHostController) {
    val items = listOf(BottomNavItem.Resources, BottomNavItem.Post, BottomNavItem.Discussion, BottomNavItem.News);
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState();
        val currentRoute = navBackStackEntry?.destination?.route;
        items.forEach { item ->
            NavigationBarItem(icon = { Icon(item.icon, contentDescription = item.label) }, label = { Text(item.label) }, selected = currentRoute == item.route, onClick = { navController.navigate(item.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } } )
        }
    }
}

@Composable
fun PlaceholderScreen(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Welcome to the $label Screen!", style = MaterialTheme.typography.headlineMedium) }
}