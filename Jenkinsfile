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

        stage('Verify Project') {
            steps {
                sh 'ls -la'
            }
        }
    }
}
