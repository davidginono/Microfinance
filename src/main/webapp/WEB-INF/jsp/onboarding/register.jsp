<%@ include file="head.jspf" %>
<c:url value="/finance/clients/onboarding" var="registerUrl"/>
<section class="erp-table-wrap" data-resource-table="client-onboarding" data-group-register data-table-selection="single">
    <h2><spring:message code="kyc.title"/></h2>
    <div class="accounting-actions" data-table-toolbar-for="client-onboarding">
        <button type="button" class="app-btn btn-neutral" data-table-command data-group-command="view" disabled><spring:message code="group.open"/></button>
        <span class="sr-only" data-group-selection-status aria-live="polite"></span>
        <sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_CREATE')"><a class="app-btn btn-launch" href="${registerUrl}/new"><spring:message code="kyc.create"/></a></sec:authorize>
    </div>
    <form method="get" action="${registerUrl}" data-table-filter-for="client-onboarding">
        <label class="sr-only" for="kycSearch"><spring:message code="common.search"/></label>
        <input class="aws-control" id="kycSearch" name="search" value="<c:out value='${search}'/>" maxlength="100"/>
        <label class="sr-only" for="kycState"><spring:message code="kyc.field.state"/></label>
        <select class="aws-control" id="kycState" name="state"><option value=""><spring:message code="kyc.allStates"/></option><c:forEach items="${['NOT_STARTED','DRAFT','SUBMITTED','VERIFY','RETURN','REJECT','REVOKE','EXPIRED','STALE']}" var="s"><option value="${s}" ${state==s?'selected':''}><spring:message code="kyc.state.${s}"/></option></c:forEach></select>
        <input type="hidden" name="sort" value="<c:out value='${sort}'/>"/>
        <button class="app-btn btn-neutral" type="submit"><spring:message code="common.search"/></button>
    </form>
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr>
        <th><span class="sr-only"><spring:message code="voucher.select"/></span></th>
        <c:forEach items="${['number','name','state','due']}" var="field"><th scope="col" data-table-sort-asc="${field}_asc" data-table-sort-desc="${field}_desc"><spring:message code="kyc.field.${field}"/></th></c:forEach>
    </tr></thead><tbody>
        <c:forEach items="${records.rows}" var="row"><tr data-group-row="${row.id}" data-target="${registerUrl}/${row.id}">
            <td><input type="checkbox" aria-label="<spring:message code='voucher.select'/> <c:out value='${row.number}'/>"/></td>
            <td data-group-identity><a class="resource-table-id-link" href="${registerUrl}/${row.id}"><c:out value="${row.number}"/></a></td><td><c:out value="${row.name}"/></td>
            <td><spring:message code="kyc.state.${row.state}"/></td><td><c:out value="${appTime:display(row.reviewDue)}"/></td>
        </tr></c:forEach>
        <c:if test="${empty records.rows}"><tr><td colspan="5"><spring:message code="${empty search and empty state?'kyc.empty':'credit.noMatches'}"/></td></tr></c:if>
    </tbody></table></div>
    <div data-table-pagination data-page="${records.page}" data-table-for="client-onboarding">
        <c:if test="${records.page>0}"><c:url value="/finance/clients/onboarding" var="previous"><c:param name="page" value="${records.page-1}"/><c:param name="search" value="${search}"/><c:param name="state" value="${state}"/><c:param name="sort" value="${sort}"/></c:url><a data-page-direction="previous" href="${previous}"><spring:message code="common.previous"/></a></c:if>
        <c:if test="${records.hasNext}"><c:url value="/finance/clients/onboarding" var="next"><c:param name="page" value="${records.page+1}"/><c:param name="search" value="${search}"/><c:param name="state" value="${state}"/><c:param name="sort" value="${sort}"/></c:url><a data-page-direction="next" href="${next}"><spring:message code="common.next"/></a></c:if>
    </div>
</section>
<script src="<c:url value='/js/group-lending.js'/>" defer></script>
<%@ include file="../fragments/footer.jspf" %>
