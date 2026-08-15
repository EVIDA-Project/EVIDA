pipeline {
    agent any

    environment {
        ANDROID_HOME = '/opt/android-sdk'
        ANDROID_SDK_ROOT = '/opt/android-sdk'
        PATH = "/opt/android-sdk/platform-tools:/opt/android-sdk/cmdline-tools/latest/bin:${env.PATH}"
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'EVIDA CI/CD pipeline started'
            }
        }

        stage('Verify Java') {
            steps {
                sh 'java -version'
            }
        }

        stage('Verify Android SDK') {
            steps {
                sh 'echo "ANDROID_HOME=$ANDROID_HOME"'
                sh 'echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"'
                sh 'ls -la $ANDROID_HOME/platforms'
            }
        }

        stage('Prepare Gradle') {
            steps {
                sh 'chmod +x gradlew'
            }
        }

        stage('Run Unit Tests') {
            steps {
                sh './gradlew testDebugUnitTest --no-daemon'
            }
        }

        stage('Run Android Lint') {
            steps {
                sh './gradlew lintDebug --no-daemon'
            }
        }

        stage('Build Android APK') {
            steps {
                sh './gradlew assembleDebug --no-daemon'
            }
        }

        stage('Verify APK') {
            steps {
                sh 'ls -lh app/build/outputs/apk/debug/'
            }
        }

        stage('Upload APK to S3') {
            steps {
                sh '''
                    aws s3 cp \
                    app/build/outputs/apk/debug/app-debug.apk \
                    s3://evida-cicd-artifacts-2026/app-debug.apk
                '''
            }
        }

        stage('Generate APK Download URL') {
            steps {
                sh '''
                    URL=$(aws s3 presign \
                        s3://evida-cicd-artifacts-2026/app-debug.apk \
                        --expires-in 3600)

                    echo "=========================================="
                    echo "APK DOWNLOAD URL (valid for 1 hour):"
                    echo "$URL"
                    echo "=========================================="
                '''
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}
