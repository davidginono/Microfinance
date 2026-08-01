<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:url var="previewInlineHref" value="${previewInlineUrl}" />
<c:url var="previewDownloadHref" value="${previewDownloadUrl}" />

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Documents / Attachment Preview</p>
    <h1 class="erp-page-title">Disbursement Proof Preview</h1>
    <p class="erp-page-subtitle"><c:out value="${previewFileName}" /></p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="erp-widget-title">Attachment</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink"><c:out value="${previewFileName}" /></h2>
            </div>
            <div class="flex flex-wrap gap-2">
                <a href="${fn:escapeXml(previewInlineHref)}" target="_blank" rel="noopener" class="app-btn btn-neutral">Open Raw File</a>
                <a href="${fn:escapeXml(previewDownloadHref)}" class="app-btn btn-primary">Download</a>
            </div>
        </div>
    </div>
    <div class="erp-panel-body">
        <c:choose>
            <c:when test="${previewIsImage}">
                <div class="overflow-auto rounded-lg border border-slate-200 bg-slate-950/95 p-4">
                    <img src="${fn:escapeXml(previewInlineHref)}" alt="${fn:escapeXml(previewFileName)}" class="mx-auto block h-auto max-w-full rounded-md bg-white" />
                </div>
            </c:when>
            <c:when test="${previewIsPdf}">
                <div class="overflow-hidden rounded-lg border border-slate-200 bg-slate-100">
                    <iframe src="${fn:escapeXml(previewInlineHref)}" title="${fn:escapeXml(previewFileName)}" class="h-[75vh] w-full border-0"></iframe>
                </div>
            </c:when>
            <c:otherwise>
                <div class="rounded-lg border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                    This file type cannot be previewed inside the page yet. Use <strong>Open Raw File</strong> or <strong>Download</strong>.
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
