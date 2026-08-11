pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    TS_WEBQUERY_APIKEY = credentials('TEAMSPEAK_WEBQUERY_APIKEY')
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
