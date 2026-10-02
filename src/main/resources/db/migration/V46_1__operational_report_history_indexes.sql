-- Retained template history must be checked before institution/member deletion.
CREATE INDEX ix_operational_template_creator ON operational_report_templates(created_by,id);
CREATE INDEX ix_operational_template_maker ON operational_report_template_versions(made_by,id);
CREATE INDEX ix_operational_template_checker ON operational_report_template_versions(checked_by,id) WHERE checked_by IS NOT NULL;
