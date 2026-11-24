<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<div class="accounting-workspace">
    <section data-resource-table="accounting-loans-clients-1" class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar">
            <h2 class="erp-panel-title" data-workspace-title><spring:message code="recording.clientLedgers" /></h2>
        </div>
        <form class="voucher-filters" method="get" action="<c:url value='/finance/client-ledgers' />">
            <label><spring:message code="voucher.search" /><input class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" /></label>
            <button class="app-btn btn-neutral"><spring:message code="voucher.search" /></button>
        </form>
        <div class="erp-table-scroll">
            <table class="erp-table">
                <thead>
                    <tr>
                        <th scope="col" data-table-order="client"><spring:message code="simpleGroup.clientNumber" /></th>
                        <th scope="col" data-table-order="name"><spring:message code="recording.ledgerClientName" /></th>
                        <th scope="col" data-table-order="status"><spring:message code="recording.ledgerPortfolio" /></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach items="${clients.rows}" var="client">
                        <tr>
                            <td><a class="resource-table-id-link" href="<c:url value='/finance/client-ledgers/${client.id}' />"><c:out value="${client.number}" /></a></td>
                            <td><c:out value="${client.name}" /></td>
                            <td><spring:message code="recording.ledgerPortfolio.${client.portfolioStatus}" /></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty clients.rows}">
                        <tr><td colspan="3"><spring:message code="voucher.noMatches" /></td></tr>
                    </c:if>
                </tbody>
            </table>
        </div>
        <div data-table-pagination data-page="${clients.page}" data-table-for="accounting-loans-clients-1" class="voucher-pagination">
            <c:if test="${clients.page>0}">
                <c:url value="/finance/client-ledgers" var="prev"><c:param name="page" value="${clients.page-1}" /><c:param name="search" value="${search}" /></c:url>
                <a data-page-direction="previous" href="<c:out value='${prev}' />"><spring:message code="common.previous" /></a>
            </c:if>
            <c:if test="${clients.hasNext}">
                <c:url value="/finance/client-ledgers" var="next"><c:param name="page" value="${clients.page+1}" /><c:param name="search" value="${search}" /></c:url>
                <a data-page-direction="next" href="<c:out value='${next}' />"><spring:message code="common.next" /></a>
            </c:if>
        </div>
    </section>
</div>
<%@ include file="../../fragments/footer.jspf" %>
