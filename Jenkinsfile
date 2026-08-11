pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    TS_QUERY_ADMIN_PASSWORD = credentials('TEAMSPEAK_QUERY_ADMIN_PASSWORD')
  }

  stages {
    stage('Deploy') {
      steps {
        sh 'docker compose -f docker-compose-status-service.yml up --build -d --remove-orphans'
      }
    }
  }

  post {
    always {
      sh 'docker image prune -f'
    }
  }
}
