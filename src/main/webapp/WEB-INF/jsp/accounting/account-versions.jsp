<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="account-version-shell.jspf" %>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="coav.title" /></h1></div>
<div class="accounting-workspace accounting-resource-workspace">
 <p><spring:message code="coav.scopeHelp" /></p>
 <section class="erp-table-wrap" data-resource-table="account-versions" data-table-selection="single" data-table-page-size="${size}" id="account-version-register" data-base-url="<c:url value='/finance/accounts/versions/' />">
  <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="coav.register" /></h2><div class="accounting-actions"><button type="button" class="app-btn btn-neutral" id="account-version-view" disabled><spring:message code="coav.view" /></button></div></div>
  <form method="get" action="<c:url value='/finance/accounts/versions' />" class="erp-table-toolbar coa-filters">
   <input type="hidden" name="sort" value="<c:out value='${sort}' />" /><input type="hidden" name="direction" value="<c:out value='${direction}' />" />
   <label class="erp-table-toolbar__search"><spring:message code="coa.search" /><input class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" /></label>
   <label class="erp-table-toolbar__control"><spring:message code="coa.status" /><select class="aws-control" name="state"><option value=""><spring:message code="coa.all" /></option><c:forEach items="${['DRAFT','PENDING','APPROVED','REJECTED','WITHDRAWN']}" var="s"><option value="${s}" ${state==s?'selected':''}><spring:message code="coav.state.${s}" /></option></c:forEach></select></label>
   <div class="erp-table-toolbar__actions"><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/versions' />"><spring:message code="coa.clear" /></a></div>
  </form>
  <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><span class="sr-only"><spring:message code="coa.select" /></span></th>
   <c:forEach items="${['code','name','number','operation','state','creator','created']}" var="col"><th data-table-sort-key="${col}" data-table-sort-asc="asc" data-table-sort-desc="desc"><spring:message code="coav.column.${col}" /></th></c:forEach>
  </tr></thead><tbody>
   <c:forEach items="${versions.rows}" var="v"><tr data-version-id="${v.id}"><td><input type="checkbox" name="account-version-selection" aria-label="<spring:message code='coa.select' /> <c:out value='${v.proposed.code}' /> · <spring:message code='coav.versionNumber' arguments='${v.number}' />" /></td><td><a class="resource-table-id-link" href="<c:url value='/finance/accounts/versions/${v.id}' />"><c:out value="${v.proposed.code}" /></a></td><td><c:out value="${v.proposed.name}" /></td><td><c:out value="${v.number}" /></td><td><spring:message code="coav.operation.${v.operation}" /></td><td><spring:message code="coav.state.${v.state}" /></td><td><c:out value="${v.creatorName}" /></td><td><c:out value="${v.getCreatedLabel()}" /></td></tr></c:forEach>
   <c:if test="${empty versions.rows}"><tr><td colspan="8"><spring:message code="coav.empty" /></td></tr></c:if>
  </tbody></table></div>
  <div data-table-pagination data-page="${versions.page}" data-table-for="account-versions">
   <c:forEach begin="0" end="1" var="way"><c:if test="${way==0 and versions.page>0 or way==1 and versions.hasNext}"><c:url value="/finance/accounts/versions" var="pageUrl"><c:param name="page" value="${versions.page+(way==0?-1:1)}" /><c:param name="size" value="${size}" /><c:param name="search" value="${search}" /><c:param name="state" value="${state}" /><c:param name="sort" value="${sort}" /><c:param name="direction" value="${direction}" /></c:url><a data-page-direction="${way==0?'previous':'next'}" href="<c:out value='${pageUrl}' />"><spring:message code="${way==0?'common.previous':'common.next'}" /></a></c:if></c:forEach>
  </div>
 </section>
</div>
<script src="<c:url value='/js/account-versions.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
