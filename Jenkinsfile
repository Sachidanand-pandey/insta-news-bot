pipeline {
    agent any

    triggers {
        // 6:00 AM and 6:00 PM India time. The TZ line applies to every schedule below it.
        cron('TZ=Asia/Kolkata\n0 6 * * *\n0 18 * * *')
    }

    options {
        disableConcurrentBuilds()
        timeout(time: 25, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        // Create these as "Secret text" credentials in Jenkins (see README)
        IG_USER_ID      = credentials('ig-user-id')
        IG_ACCESS_TOKEN = credentials('ig-access-token')
        IMGBB_API_KEY   = credentials('imgbb-api-key')

        BRAND_NAME  = 'Daily News'
        STORY_COUNT = '4'
        BOT_TZ      = 'Asia/Kolkata'
        // Persists in the job workspace between runs so the evening post never repeats the morning one
        STATE_FILE  = 'state/posted.txt'
    }

    stages {
        stage('Build') {
            steps {
                bat 'mvn -B -q -DskipTests compile'
            }
        }

        stage('Post to Instagram') {
            steps {
                // "auto" picks Morning (before noon IST) or Evening (after noon IST)
                // No retry here on purpose: a retry after a timeout could publish twice.
                bat 'mvn -B -q exec:java -Dexec.args=auto'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'output/**/*.jpg', allowEmptyArchive: true
        }
        failure {
            echo 'Instagram post failed. Check the console log; an expired access token is the most common cause.'
        }
    }
}