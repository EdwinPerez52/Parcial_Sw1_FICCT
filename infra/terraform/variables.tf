variable "aws_region" {
  type    = string
  default = "us-east-1"
}
variable "environment" {
  type    = string
  default = "dev"
}
variable "project_name" {
  type    = string
  default = "collab-modeler"
}
variable "api_image" {
  type        = string
  description = "URI inmutable de la imagen API en ECR"
}
variable "google_client_id" {
  type      = string
  sensitive = true
}
variable "google_client_secret" {
  type      = string
  sensitive = true
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

locals {
  name = "${var.project_name}-${var.environment}"
  tags = {
    Project = var.project_name, Environment = var.environment, ManagedBy = "Terraform"
  }
}



