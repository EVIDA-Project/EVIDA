# 🚀 EVIDA --- Android CI/CD Pipeline

```{=html}
<p align="center">
```
`<strong>`{=html}Automated Build • Test • Lint • Artifact
Delivery`</strong>`{=html}`<br>`{=html} A Jenkins-based CI/CD pipeline
for the EVIDA Android application, with AWS S3 used for APK artifact
storage.
```{=html}
</p>
```
```{=html}
<p align="center">
```
`<img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android" alt="Android">`{=html}
`<img src="https://img.shields.io/badge/CI%2FCD-Jenkins-red?style=for-the-badge&logo=jenkins" alt="Jenkins">`{=html}
`<img src="https://img.shields.io/badge/Source-GitHub-black?style=for-the-badge&logo=github" alt="GitHub">`{=html}
`<img src="https://img.shields.io/badge/Cloud-AWS-orange?style=for-the-badge&logo=amazonaws" alt="AWS">`{=html}
`<img src="https://img.shields.io/badge/Artifact-Amazon%20S3-blue?style=for-the-badge&logo=amazons3" alt="Amazon S3">`{=html}
`<img src="https://img.shields.io/badge/IaC-Terraform-purple?style=for-the-badge&logo=terraform" alt="Terraform">`{=html}
```{=html}
</p>
```

------------------------------------------------------------------------

## 📌 Overview

**EVIDA** uses a Jenkins CI/CD pipeline to automate the process of
taking Android source code from GitHub through validation, testing,
static analysis, APK generation, and artifact delivery.

The pipeline is designed so that a failure in an important quality stage
prevents the later delivery stages from running.

### ✨ What the pipeline does

``` text
Developer
    │
    ▼
GitHub Repository
    │
    ▼
Jenkins
    │
    ├── Verify Java
    ├── Verify Android SDK
    ├── Prepare Gradle
    ├── Run Unit Tests
    ├── Run Android Lint
    ├── Build Android APK
    ├── Verify APK
    ├── Upload APK → Amazon S3
    ├── Generate Presigned Download URL
    └── Cleanup Jenkins Workspace
```

------------------------------------------------------------------------

# 🏗️ CI/CD Architecture

``` mermaid
flowchart LR
    A["👩‍💻 Developer<br/>Code Changes"] --> B["🐙 GitHub<br/>EVIDA Repository"]
    B --> C["🔧 Jenkins<br/>CI/CD Server"]

    C --> D["☕ Verify Java"]
    D --> E["🤖 Verify Android SDK"]
    E --> F["⚙️ Prepare Gradle"]
    F --> G["🧪 Unit Tests"]
    G --> H["🔎 Android Lint"]
    H --> I["📦 Build Android APK"]
    I --> J["✅ Verify APK"]
    J --> K["☁️ Upload APK to S3"]
    K --> L["🔗 Generate Presigned URL"]
    L --> M["🧹 Workspace Cleanup"]

    G -. "Failure → Stop" .-> X["❌ Pipeline Failed"]
    H -. "Failure → Stop" .-> X
    I -. "Failure → Stop" .-> X

    M --> N["✅ Pipeline Complete"]
```

------------------------------------------------------------------------

# 🔄 Pipeline Stages

  -------------------------------------------------------------------------
                            \# Stage                 Purpose
  ---------------------------- --------------------- ----------------------
                             1 **Checkout SCM**      Retrieves the source
                                                     code from the
                                                     configured GitHub
                                                     branch.

                             2 **Checkout**          Confirms the working
                                                     source is available in
                                                     the Jenkins workspace.

                             3 **Verify Java**       Checks that the
                                                     required Java
                                                     environment is
                                                     available.

                             4 **Verify Android      Confirms that the
                               SDK**                 Android SDK is
                                                     installed and
                                                     accessible.

                             5 **Prepare Gradle**    Makes the Gradle
                                                     wrapper executable so
                                                     Jenkins can run Gradle
                                                     commands.

                             6 **Run Unit Tests**    Runs the Android
                                                     unit-test suite using
                                                     `testDebugUnitTest`.

                             7 **Run Android Lint**  Performs static
                                                     analysis using
                                                     `lintDebug`.

                             8 **Build Android APK** Builds the debug APK
                                                     using `assembleDebug`.

                             9 **Verify APK**        Checks that the
                                                     generated APK exists
                                                     in the expected output
                                                     directory.

                            10 **Upload APK to S3**  Stores `app-debug.apk`
                                                     as a build artifact in
                                                     Amazon S3.

                            11 **Generate APK        Creates a time-limited
                               Download URL**        S3 presigned URL for
                                                     downloading the
                                                     artifact.

                            12 **Cleanup**           Cleans the Jenkins
                                                     workspace after the
                                                     pipeline execution.
  -------------------------------------------------------------------------

------------------------------------------------------------------------

# 🛡️ Quality Gates

The pipeline is not only a build script. It contains quality gates
before artifact delivery.

### 🧪 Unit Testing

``` bash
./gradlew testDebugUnitTest --no-daemon
```

Validates the application's unit tests.

**If the tests fail → the pipeline stops.**

### 🔎 Android Lint

``` bash
./gradlew lintDebug --no-daemon
```

Performs static code analysis and checks for Android code-quality
issues.

The project also contains:

``` text
lint.xml
```

which configures the handling of the `PropertyEscape` lint issue
encountered with the Windows Android SDK path.

**If lint fails → the pipeline stops.**

### 📦 APK Build

``` bash
./gradlew assembleDebug --no-daemon
```

Generates:

``` text
app/build/outputs/apk/debug/app-debug.apk
```

------------------------------------------------------------------------

# ☁️ AWS S3 Artifact Delivery

After a successful build, Jenkins uploads the generated APK to an Amazon
S3 bucket.

``` text
Android APK
     │
     ▼
Amazon S3
     │
     ▼
Presigned Download URL
     │
     ▼
Temporary APK Download
```

The download URL is **time-limited** rather than exposing the S3 object
permanently through a public URL.

### Artifact

``` text
app-debug.apk
```

### Presigned URL

The pipeline generates a temporary download URL with a **1-hour validity
period**.

> 🔐 The S3 bucket can remain private while the presigned URL provides
> temporary access to the specific artifact.

------------------------------------------------------------------------

# 🧱 Infrastructure as Code

The AWS infrastructure used by the project is represented under:

``` text
terraform/
```

The Terraform configuration includes the infrastructure required for the
CI/CD environment, including the Jenkins host and S3-related resources
used by the project.

Typical Terraform workflow:

``` bash
terraform init
terraform plan
terraform apply
```

> ⚠️ Do not commit AWS credentials, private keys, Terraform state files,
> or other secrets to GitHub.

------------------------------------------------------------------------

# 📁 Project Structure

``` text
EVIDA/
│
├── app/                         # Android application source
│
├── gradle/                      # Gradle wrapper and configuration
│
├── build.gradle.kts             # Root Gradle build configuration
├── settings.gradle.kts          # Gradle project settings
├── gradle.properties            # Gradle properties
│
├── gradlew                      # Gradle wrapper for Linux/macOS/Jenkins
├── gradlew.bat                  # Gradle wrapper for Windows
│
├── Jenkinsfile                  # CI/CD pipeline definition
├── lint.xml                     # Android Lint configuration
│
├── terraform/                   # Infrastructure as Code
│   ├── main.tf
│   ├── provider.tf
│   └── .terraform.lock.hcl
│
├── .gitignore                   # Git ignore rules
│
└── PHASE_1_FORENSIC_REPORT.md   # Project documentation
```

------------------------------------------------------------------------

# ⚙️ Jenkins Environment

The Jenkins server requires the tools used by the pipeline to be
available on the Jenkins machine.

### Required environment

-   ☕ Java / JDK
-   🤖 Android SDK
-   ⚙️ Gradle Wrapper
-   🔧 Jenkins
-   ☁️ AWS CLI
-   🔐 AWS permissions for the required S3 operations
-   🐙 Git

The Android SDK path is configured locally on the Jenkins environment
rather than being committed as a machine-specific Windows path.

------------------------------------------------------------------------

# 🚦 How the Pipeline Works

### 1️⃣ Developer changes the code

The developer modifies the EVIDA Android application.

### 2️⃣ Code is pushed to GitHub

The source code and `Jenkinsfile` are stored in the GitHub repository.

### 3️⃣ Jenkins checks out the configured branch

Jenkins retrieves the latest source code from the branch configured in
the Jenkins job.

For the development/CI-CD workflow, this can be the:

``` text
CICD
```

branch.

After the work is merged into the production/default branch, Jenkins can
be configured to build:

``` text
main
```

instead.

### 4️⃣ Jenkins validates the environment

The pipeline verifies Java and the Android SDK.

### 5️⃣ Tests and lint run

The application is checked before the APK is produced.

``` text
Unit Tests
     ↓
Android Lint
     ↓
Build
```

### 6️⃣ APK is generated

Gradle creates the debug APK.

### 7️⃣ APK is verified

Jenkins checks that the expected artifact exists.

### 8️⃣ APK is uploaded to S3

The generated artifact is stored in Amazon S3.

### 9️⃣ Temporary download URL is generated

Jenkins generates a presigned URL valid for one hour.

### 🔟 Workspace is cleaned

The Jenkins workspace is cleaned so that the next build starts from a
clean environment.

------------------------------------------------------------------------

# 🧩 Main Components & Their Roles

  -----------------------------------------------------------------------
  Component                           Role
  ----------------------------------- -----------------------------------
  👩‍💻 **Developer**                    Creates and modifies application
                                      code.

  🐙 **GitHub**                       Stores source code, branches,
                                      Jenkinsfile and project
                                      configuration.

  🔧 **Jenkins**                      Automates the complete CI/CD
                                      workflow.

  ☕ **Java**                         Provides the runtime required for
                                      the Android/Gradle build
                                      environment.

  🤖 **Android SDK**                  Provides Android build tools and
                                      platform components.

  ⚙️ **Gradle**                       Compiles, tests, lints and packages
                                      the Android application.

  🧪 **Unit Tests**                   Validate application behaviour
                                      through automated tests.

  🔎 **Android Lint**                 Performs static analysis and
                                      detects code-quality issues.

  📦 **APK**                          The generated Android application
                                      artifact.

  ☁️ **Amazon S3**                    Stores the generated APK artifact.

  🔗 **Presigned URL**                Provides temporary access to the
                                      private S3 artifact.

  🧱 **Terraform**                    Defines and provisions cloud
                                      infrastructure as code.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 🔐 Security Considerations

The pipeline includes basic quality controls, but it is **not a complete
enterprise security pipeline**.

Current controls include:

-   ✅ Unit testing
-   ✅ Android Lint
-   ✅ Build validation
-   ✅ Private artifact storage with temporary presigned access
-   ✅ Git ignore rules for sensitive/local files

Potential future improvements include:

-   🔐 Secret scanning
-   🛡️ Dependency vulnerability scanning
-   🔎 SAST/security analysis
-   📦 Dependency/SBOM analysis
-   🔑 Jenkins Credentials Manager instead of plain-text credentials
-   🐳 Isolated build agents
-   📋 Artifact signing
-   🚪 Protected GitHub branches and pull-request checks

------------------------------------------------------------------------

# ▶️ Running the Project

### Local Android build

On a machine with the required Android SDK and Java environment:

``` bash
./gradlew assembleDebug
```

### Run unit tests

``` bash
./gradlew testDebugUnitTest
```

### Run lint

``` bash
./gradlew lintDebug
```

### Clean the project

``` bash
./gradlew clean
```

------------------------------------------------------------------------

# 📊 CI/CD Success Criteria

A successful pipeline should reach the final stages:

``` text
✅ Checkout
   ↓
✅ Java Verification
   ↓
✅ Android SDK Verification
   ↓
✅ Gradle Preparation
   ↓
✅ Unit Tests
   ↓
✅ Android Lint
   ↓
✅ APK Build
   ↓
✅ APK Verification
   ↓
✅ S3 Upload
   ↓
✅ Presigned URL
   ↓
✅ Cleanup
   ↓
🎉 SUCCESS
```

If a required quality/build stage fails:

``` text
❌ Test/Lint/Build Failure
        ↓
   Pipeline Stops
        ↓
   APK is not promoted
```

------------------------------------------------------------------------

# 🎯 Project Outcome

The EVIDA project demonstrates a complete, automated Android CI/CD
workflow in which source code moves from **GitHub → Jenkins → automated
validation → APK build → Amazon S3 artifact storage → temporary download
access**.

It also demonstrates the use of **Terraform for infrastructure
provisioning** and establishes a foundation that can be extended with
stronger security, automated triggers, release management, and
production-grade deployment controls.


```{=html}
<p align="center">
```
`<strong>`{=html}🚀 EVIDA --- Build it. Test it. Ship
it.`</strong>`{=html}
```{=html}
</p>
```
