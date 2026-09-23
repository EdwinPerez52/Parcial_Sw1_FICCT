output "application_url" {
  value = local.public_url
}
output "api_load_balancer" {
  value = aws_lb.api.dns_name
}
output "web_bucket" {
  value = aws_s3_bucket.web.id
}
output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.id
}
output "ecr_repository" {
  value = data.aws_ecr_repository.api.repository_url
}
output "generation_queue" {
  value = aws_sqs_queue.generation.url
}
output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.web.id
}
