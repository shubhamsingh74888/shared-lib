def call() {
    withCredentials([usernamePassword(
        credentialsId: 'aws-creds', 
        usernameVariable: 'AWS_ACCESS_KEY_ID', 
        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
    )]) {
        sh """
            export PATH=/usr/local/bin:/usr/bin:/bin:\$PATH
            
            # Go into the remote-backend folder
            cd remote-backend
            
            echo "[BOOTSTRAP] Initializing local backend..."
            terraform init

            echo "[BOOTSTRAP] Selecting workspace \${ENVIRONMENT}..."
            terraform workspace select \${ENVIRONMENT} 2>/dev/null || terraform workspace new \${ENVIRONMENT}

            # Define your unique names
            BUCKET_NAME="my-s3-bucket-wanderlust-infra-\${ENVIRONMENT}"
            TABLE_NAME="wanderlust-shubham-\${ENVIRONMENT}"

            echo "[BOOTSTRAP] Checking if DynamoDB table exists..."
            if aws dynamodb describe-table --table-name "\$TABLE_NAME" --region \${AWS_DEFAULT_REGION} 2>/dev/null; then
                echo "[BOOTSTRAP] Table exists. Importing into state..."
                terraform import aws_dynamodb_table.basic-dynamo-table "\$TABLE_NAME" || true
            fi

            echo "[BOOTSTRAP] Checking if S3 bucket exists..."
            if aws s3api head-bucket --bucket "\$BUCKET_NAME" 2>/dev/null; then
                echo "[BOOTSTRAP] Bucket exists. Importing into state..."
                terraform import aws_s3_bucket.my_s3_bucket "\$BUCKET_NAME" || true
                terraform import aws_s3_bucket_versioning.my_s3_bucket "\$BUCKET_NAME" || true
                terraform import aws_s3_bucket_server_side_encryption_configuration.my_s3_bucket "\$BUCKET_NAME" || true
                terraform import aws_s3_bucket_public_access_block.my_s3_bucket "\$BUCKET_NAME" || true
            fi

            echo "[BOOTSTRAP] Applying remote backend configuration..."
            terraform apply -auto-approve
            
            echo "[BOOTSTRAP] ✔ Remote backend is ready to use!"
        """
    }
}
