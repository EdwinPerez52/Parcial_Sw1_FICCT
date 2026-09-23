variable "aws_region" {
  type    = string
  default = "us-east-1"
  validation {
    condition     = var.aws_region == "us-east-1"
    error_message = "This initial stack deploys in us-east-1 so the ACM certificate can be used by CloudFront."
  }
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["test", "prod"], var.environment)
    error_message = "environment must be test or prod."
  }
}
variable "project_name" {
  type    = string
  default = "collab-modeler"
}
variable "vpc_cidr" {
  type = string
}
variable "api_image" {
  type        = string
  description = "Immutable ECR image URI, tagged with the full Git commit SHA."
}
variable "domain_name" {
  type     = string
  default  = null
  nullable = true
}
variable "route53_zone_id" {
  type     = string
  default  = null
  nullable = true
}
variable "nat_gateway_per_az" {
  type    = bool
  default = false
}
variable "db_instance_class" {
  type = string
}
variable "db_allocated_storage" {
  type    = number
  default = 20
}
variable "db_max_allocated_storage" {
  type    = number
  default = 100
}
variable "postgres_engine_version" {
  type    = string
  default = "17.4"
}
variable "backup_retention_days" {
  type = number
}
variable "deletion_protection" {
  type = bool
}
variable "multi_az" {
  type = bool
}
variable "redis_node_type" {
  type = string
}
variable "redis_cache_clusters" {
  type = number
}
variable "artifact_retention_days" {
  type    = number
  default = 30
}
variable "generation_visibility_timeout_seconds" {
  type    = number
  default = 900
}
variable "log_retention_days" {
  type    = number
  default = 30
}
variable "cloudfront_price_class" {
  type    = string
  default = "PriceClass_100"
}
variable "api_cpu" {
  type    = number
  default = 512
}
variable "api_memory" {
  type    = number
  default = 1024
}
variable "worker_cpu" {
  type    = number
  default = 1024
}
variable "worker_memory" {
  type    = number
  default = 2048
}
variable "api_desired_count" {
  type    = number
  default = 1
}
variable "worker_desired_count" {
  type    = number
  default = 1
}
variable "ai_api_key" {
  type      = string
  sensitive = true
  default   = ""
}
variable "ai_base_url" {
  type    = string
  default = "https://api.openai.com/v1"
}
variable "ai_text_model" {
  type    = string
  default = "gpt-4.1-mini"
}
variable "ai_vision_model" {
  type    = string
  default = "gpt-4.1-mini"
}
variable "ai_audio_model" {
  type    = string
  default = "gpt-4o-mini-transcribe"
}
variable "generation_encryption_key" {
  type      = string
  sensitive = true
}
variable "generation_download_secret" {
  type      = string
  sensitive = true
}
variable "mobile_spec_secret" {
  type      = string
  sensitive = true
}
variable "ses_smtp_username" {
  type      = string
  sensitive = true
}
variable "ses_smtp_password" {
  type      = string
  sensitive = true
}
