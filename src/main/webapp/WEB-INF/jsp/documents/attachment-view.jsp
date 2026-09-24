<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:url var="previewInlineHref" value="${previewInlineUrl}" />
<c:url var="previewDownloadHref" value="${previewDownloadUrl}" />

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Documents / Attachment Preview</p>
    <h1 class="erp-page-title"><c:out value="${previewFileName}" /></h1>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="erp-panel-title">Disbursement Proof Preview</h2>
            <div class="flex flex-wrap gap-2">
                <a href="${fn:escapeXml(previewInlineHref)}" target="_blank" rel="noopener" class="app-btn btn-neutral">Open Raw File</a>
                <a href="${fn:escapeXml(previewDownloadHref)}" class="app-btn btn-primary" data-download-action="true">Download</a>
            </div>
            </div>
        </div>
    <div class="erp-panel-body">
        <c:choose>
            <c:when test="${previewIsImage}">
                <div class="attachment-preview-frame attachment-preview-frame--image">
                    <img src="${fn:escapeXml(previewInlineHref)}" alt="${fn:escapeXml(previewFileName)}" class="mx-auto block h-auto max-w-full bg-white" />
    </div>
            </c:when>
            <c:when test="${previewIsPdf}">
                <div class="attachment-preview-frame">
                    <iframe src="${fn:escapeXml(previewInlineHref)}" title="${fn:escapeXml(previewFileName)}" class="h-[75vh] w-full border-0"></iframe>
                </div>
            </c:when>
            <c:otherwise>
                <div class="attachment-preview-frame px-4 py-4 text-sm text-slate-600">
                    This file type cannot be previewed inside the page yet. Use <strong>Open Raw File</strong> or <strong>Download</strong>.
                </div>
            </c:otherwise>
        </c:choose>
                </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
