pipeline {
    agent any

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

        stage('Build Android APK') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew assembleDebug'
            }
        }

        stage('Verify APK') {
            steps {
                sh 'ls -lh app/build/outputs/apk/debug/'
            }
        }
    }
}
