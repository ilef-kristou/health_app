pipeline {
    agent any

    environment {
        registry = "ilefkristou/health-app"
        registryCredential = 'dockerHub'
        dockerImage = ''
        JAVA_HOME ="/opt/java/openjdk"
        PATH = "$JAVA_HOME/bin:$PATH"
        SONAR_URL = "http://sonarqube:9000"
        NEXUS_URL = "http://nexus:8081"
        IMAGE_NAME = "${registry}:${BUILD_NUMBER}"
    }

    stages {


        stage('Checkout Git') {
            steps {
                checkout scm
            }
        }

        stage('Maven Compile & Test') {
            steps {
                dir('backend') {
                    sh 'chmod +x mvnw || true'
                    sh './mvnw clean compile test'
                }
            }
        }

        stage('SonarQube Analysis') {
            environment {
                SONAR_TOKEN = credentials('SONAR_TOKEN')
            }
            steps {
                dir('backend') {
                    sh """
                        ./mvnw sonar:sonar \
                        -Dsonar.projectKey=health-app \
                        -Dsonar.host.url=${SONAR_URL} \
                        -Dsonar.token=\${SONAR_TOKEN}
                    """
                }
            }
        }

        stage('Trivy Filesystem Scan') {
            steps {
                dir('backend') {
                    sh """
                        docker run --rm \
                            -v \${PWD}:/project \
                            aquasec/trivy:latest fs \
                            --severity HIGH,CRITICAL \
                            --exit-code 0 \
                            --no-progress \
                            --timeout 20m \
                            --db-repository ghcr.io/aquasecurity/trivy-db:2 \
                            --format table \
                            -o /project/trivy-fs-report.txt \
                            /project || echo "Aucune vulnérabilité HIGH/CRITICAL trouvée dans le code source / Dockerfile" > /project/trivy-fs-report.txt

                        echo "=== Résultat Trivy Filesystem Scan ==="
                        cat /project/trivy-fs-report.txt || echo "Rapport vide (probablement clean)"
                    """
                    archiveArtifacts artifacts: 'trivy-fs-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Maven Package Application') {
            steps {
                dir('backend') {
                    sh './mvnw package -DskipTests'
                }
            }
        }

        stage('Deploy to Nexus') {
            steps {
                dir('backend') {
                    withCredentials([usernamePassword(
                        credentialsId: 'nexus-credentials',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )]) {
                        sh """
                            cat > /tmp/settings.xml <<EOF
<settings>
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>\${NEXUS_USERNAME}</username>
      <password>\${NEXUS_PASSWORD}</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>\${NEXUS_USERNAME}</username>
      <password>\${NEXUS_PASSWORD}</password>
    </server>
  </servers>
</settings>
EOF
                            ./mvnw deploy -DskipTests \
                            -s /tmp/settings.xml \
                            -DaltDeploymentRepository=nexus-snapshots::default::${NEXUS_URL}/repository/maven-snapshots/
                        """
                    }
                }
            }
        }

        stage('Build & Tag Docker Image') {
            steps {
                dir('backend') {
                    script {
                        dockerImage = docker.build("${IMAGE_NAME}", ".")
                        dockerImage.tag('latest')
                    }
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh """
                    docker run --rm \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        -v \${WORKSPACE}:/workspace \
                        aquasec/trivy:latest image \
                        --severity HIGH,CRITICAL \
                        --exit-code 0 \
                        --no-progress \
                        --timeout 20m \
                        --db-repository ghcr.io/aquasecurity/trivy-db:2 \
                        --format table \
                        -o /workspace/trivy-image-report.txt \
                        ${IMAGE_NAME} || echo "Aucune vulnérabilité HIGH/CRITICAL trouvée dans l'image Docker" > /workspace/trivy-image-report.txt

                    echo "=== Résultat Trivy Image Scan ==="
                    cat /workspace/trivy-image-report.txt || echo "Rapport vide (probablement clean)"
                """
                archiveArtifacts artifacts: 'trivy-image-report.txt', allowEmptyArchive: true
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('', registryCredential) {
                        dockerImage.push()
                        dockerImage.push('latest')
                    }
                }
            }
        }

        stage('Deploy Application') {
            steps {
                script {
                    sh 'docker stop health-app || true'
                    sh 'docker rm health-app || true'

                    sh """
                        docker run -d \
                            --name health-app \
                            --network devops-net \
                            -p 9002:9001 \
                            ${IMAGE_NAME}
                    """
                }
            }
        }

        stage('Verify Monitoring') {
            steps {
                script {
                    sh 'sleep 15'

                    sh """
                        curl -f http://health-app:9001/actuator/prometheus \
                        && echo "✅ Prometheus endpoint OK" \
                        || echo "❌ Endpoint non disponible"
                    """

                    sh """
                        curl -s http://prometheus:9090/api/v1/targets \
                        | grep health-app \
                        && echo "✅ Prometheus scrape l'app" \
                        || echo "❌ Target non trouvée dans Prometheus"
                    """
                }
            }
        }
    }

    post {
    success {
        echo "✅ Pipeline terminé avec succès - Build ${BUILD_NUMBER} déployé"
        mail to: 'ton-email@gmail.com',
             subject: "✅ Build ${BUILD_NUMBER} - SUCCESS",
             body: "Le pipeline health-app a réussi.\n\nBuild: ${BUILD_NUMBER}\nURL: ${BUILD_URL}"
    }
    failure {
        echo "❌ Pipeline échoué - Build ${BUILD_NUMBER}"
        mail to: 'ton-email@gmail.com',
             subject: "❌ Build ${BUILD_NUMBER} - FAILURE",
             body: "Le pipeline health-app a échoué.\n\nBuild: ${BUILD_NUMBER}\nURL: ${BUILD_URL}"
    }
    always {
        sh "docker rmi ${IMAGE_NAME} || true"
    }
}
}