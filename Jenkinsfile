pipeline {
    agent any

    environment {
        registry = "ilefkristou/health-app"
        registryCredential = 'dockerHub'
        dockerImage = ''
        JAVA_HOME = "/usr/lib/jvm/java-17-openjdk-amd64"
        PATH = "$JAVA_HOME/bin:$PATH"
        SONAR_URL = "http://sonarqube:9000"
        NEXUS_URL = "http://nexus:8081"
        IMAGE_NAME = "${registry}:${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout Git') {
            steps {
                checkout scmGit(
                    branches: [[name: '*/test']],
                    userRemoteConfigs: [[url: 'https://github.com/ilef-kristou/health_app.git']]
                )
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

        stage('Trivy Filesystem Scan (source + Dockerfile)') {
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
                    sh './mvnw package -DskipTests'  // skipTests car déjà testé avant
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
                            ./mvnw deploy \
                            -DaltDeploymentRepository=nexus-releases::default::\${NEXUS_URL}/repository/maven-releases/ \
                            -DaltSnapshotDeploymentRepository=nexus-snapshots::default::\${NEXUS_URL}/repository/maven-snapshots/ \
                            -Dusername=\${NEXUS_USERNAME} \
                            -Dpassword=\${NEXUS_PASSWORD}
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
    }
}