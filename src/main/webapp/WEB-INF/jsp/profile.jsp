<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="fragments/header.jspf" %>
<%@ include file="fragments/sidebar.jspf" %>
<%@ include file="fragments/alerts.jspf" %>

<style>
    .profile-photo-dropzone {
        border: 1px dashed #b8c7d8;
        background: #f8fafc;
        transition: border-color 160ms ease, background-color 160ms ease;
    }
    .profile-photo-dropzone.is-dragover {
        border-color: #0284c7;
        background: #f0f9ff;
    }
    .profile-photo-preview img {
        display: block;
        height: 9.375rem;
        width: 7.5rem;
        object-fit: cover;
    }
    .profile-photo-fallback {
        height: 9.375rem;
        width: 7.5rem;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="profile.page.breadcrumb" text="Workspace / Profile" /></p>
    <h1 class="erp-page-title"><spring:message code="profile.page.title" text="Profile" /></h1>
    <p class="erp-page-subtitle"><spring:message code="profile.page.subtitle" text="Update your passport photo for workspace and loan reviews." /></p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="profile.photo.eyebrow" text="Profile Photo" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="profile.photo.title" text="Passport Photo" /></h2>
    </div>
    <div class="erp-panel-body">
        <div class="grid gap-5 lg:grid-cols-[auto_minmax(0,1fr)]">
            <div class="profile-photo-preview rounded-md border border-slate-200 bg-white p-3 shadow-sm">
                <img id="profilePhotoPreview"
                     src="<c:url value='/profile/image/me' />"
                     alt="<spring:message code='profile.photo.currentAlt' text='Current profile photo' />"
                     onerror="this.classList.add('hidden'); this.nextElementSibling.classList.remove('hidden');" />
                <div class="profile-photo-fallback hidden flex items-center justify-center rounded-md bg-slate-50 text-slate-500">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M20 21a8 8 0 0 0-16 0" />
                        <circle cx="12" cy="8" r="4" />
                    </svg>
                </div>
            </div>

            <form id="profilePhotoForm" action="/profile/image" method="post" enctype="multipart/form-data" class="space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label id="profilePhotoDropzone" class="profile-photo-dropzone block rounded-md p-5 text-center">
                    <span class="block text-sm font-semibold text-slate-800"><spring:message code="profile.photo.dropTitle" text="Drop your passport photo here" /></span>
                    <span class="mt-1 block text-sm text-slate-500"><spring:message code="profile.photo.dropHelp" text="or choose a PNG/JPEG image up to 2 MB. It will be cropped to 4:5 passport size." /></span>
                    <input id="profileImageInput" type="file" name="profileImage" accept="image/png,image/jpeg" class="mt-4 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700" required />
                </label>
                <div class="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    <div class="font-semibold text-slate-800">${profileMember.fullName}</div>
                    <div class="mt-1">${profileMember.memberNo}</div>
                    <c:if test="${not empty profileMember.email}">
                        <div class="mt-1">${profileMember.email}</div>
                    </c:if>
                </div>
                <div class="flex flex-wrap items-center justify-end gap-2">
                    <a href="javascript:history.back()" class="app-btn btn-neutral"><spring:message code="common.back" text="Back" /></a>
                    <button type="submit" class="app-btn btn-primary"><spring:message code="profile.photo.save" text="Save Photo" /></button>
                </div>
            </form>
        </div>
    </div>
</section>

<script>
    (function () {
        var dropzone = document.getElementById('profilePhotoDropzone');
        var input = document.getElementById('profileImageInput');
        var preview = document.getElementById('profilePhotoPreview');
        if (!dropzone || !input || !preview) {
            return;
        }
        ['dragenter', 'dragover'].forEach(function (eventName) {
            dropzone.addEventListener(eventName, function (event) {
                event.preventDefault();
                dropzone.classList.add('is-dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (eventName) {
            dropzone.addEventListener(eventName, function (event) {
                event.preventDefault();
                dropzone.classList.remove('is-dragover');
            });
        });
        dropzone.addEventListener('drop', function (event) {
            if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length) {
                input.files = event.dataTransfer.files;
                updatePreview(event.dataTransfer.files[0]);
            }
        });
        input.addEventListener('change', function () {
            updatePreview(input.files && input.files.length ? input.files[0] : null);
        });
        function updatePreview(file) {
            if (!file || !file.type || file.type.indexOf('image/') !== 0) {
                return;
            }
            preview.src = URL.createObjectURL(file);
            preview.classList.remove('hidden');
            if (preview.nextElementSibling) {
                preview.nextElementSibling.classList.add('hidden');
            }
        }
    })();
</script>

<%@ include file="fragments/footer.jspf" %>
