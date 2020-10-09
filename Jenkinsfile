pipeline {
	agent any
	stages {
		stage('Build') {
			steps {
				sh 'rm -f private.gradle'
				sh './gradlew clean build'
				archive 'build/libs/*jar'
			}
		}
		stage('DeployStable') {
			when {
				branch 'stable'
			}
			steps {
				withCredentials([file(credentialsId: 'privateStandard', variable: 'PRIVATEGRADLE')]) {
					sh '''
						rm -rf private.gradle
						cp "$PRIVATEGRADLE" private.gradle
						./gradlew publish
					'''
				}
			}
		}
		stage('DeploySnapshot') {
			steps {
				withCredentials([file(credentialsId: 'privateSnapshot', variable: 'PRIVATEGRADLE')]) {
					sh '''
						rm -rf private.gradle
						cp "$PRIVATEGRADLE" private.gradle
						./gradlew publish
					'''
				}
			}
		}
	}
}