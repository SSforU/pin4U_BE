variable "db_password" {
  description = "RDS master password. Inject via TF_VAR_db_password or terraform.tfvars (gitignored)."
  type        = string
  sensitive   = true
}

variable "ci_iam_arn" {
  description = "IAM ARN for CI/CD (GitHub Actions). Used for ECR push access."
  type        = string
  default     = ""
}