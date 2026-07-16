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
    .profile-photo-preview {
        display: inline-flex;
        width: fit-content;
        max-width: 100%;
        flex-direction: column;
        align-items: center;
        justify-self: center;
        gap: 0.55rem;
    }
    .profile-photo-frame {
        position: relative;
        display: grid;
        place-items: center;
        height: 11.25rem;
        width: 11.25rem;
        overflow: hidden;
        border: 1px solid #d7e1ea;
        border-radius: 9999px;
        background: #ffffff;
        touch-action: none;
        user-select: none;
    }
    .profile-photo-frame img {
        display: block;
        position: absolute;
        left: 0;
        top: 0;
        height: 100%;
        width: 100%;
        max-width: none;
        object-fit: cover;
        cursor: grab;
        will-change: left, top, width, height;
    }
    .profile-photo-frame img.is-dragging {
        cursor: grabbing;
    }
    .profile-photo-fallback {
        position: absolute;
        inset: 0;
    }
    .profile-photo-crop-border {
        pointer-events: none;
        position: absolute;
        inset: 0;
        border: 2px dashed rgba(14, 116, 144, 0.86);
        border-radius: 9999px;
        box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72), 0 0 0 999px rgba(15, 23, 42, 0.05);
    }
    .profile-photo-crop-border::before {
        content: "";
        position: absolute;
        inset: 0;
        background:
            linear-gradient(to right, transparent 33.333%, rgba(14, 116, 144, 0.22) 33.333%, rgba(14, 116, 144, 0.22) calc(33.333% + 1px), transparent calc(33.333% + 1px), transparent 66.666%, rgba(14, 116, 144, 0.22) 66.666%, rgba(14, 116, 144, 0.22) calc(66.666% + 1px), transparent calc(66.666% + 1px)),
            linear-gradient(to bottom, transparent 33.333%, rgba(14, 116, 144, 0.22) 33.333%, rgba(14, 116, 144, 0.22) calc(33.333% + 1px), transparent calc(33.333% + 1px), transparent 66.666%, rgba(14, 116, 144, 0.22) 66.666%, rgba(14, 116, 144, 0.22) calc(66.666% + 1px), transparent calc(66.666% + 1px));
    }
    .profile-photo-frame-label {
        color: #64748b;
        font-size: 0.76rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-align: center;
    }
    .profile-photo-editor {
        width: min(100%, 22rem);
        margin-inline: auto;
    }
    .profile-photo-editor input[type="range"] {
        width: 100%;
        accent-color: #0284c7;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="profile.page.breadcrumb" text="Workspace / Profile" /></p>
    <h1 class="erp-page-title"><spring:message code="profile.page.title" text="Profile" /></h1>
    <p class="erp-page-subtitle"><spring:message code="profile.page.subtitle" text="Update your profile photo for workspace and loan reviews." /></p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="profile.photo.eyebrow" text="Profile Photo" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="profile.photo.title" text="Passport Photo" /></h2>
    </div>
    <div class="erp-panel-body">
        <div class="grid gap-5 lg:grid-cols-[auto_minmax(0,1fr)]">
            <div class="profile-photo-preview rounded-md border border-slate-200 bg-white p-3 shadow-sm">
                <div id="profilePhotoFrame" class="profile-photo-frame">
                    <img id="profilePhotoPreview"
                         src="<c:url value='/profile/image/me' />"
                         alt="<spring:message code='profile.photo.currentAlt' text='Current profile photo' />"
                         onerror="this.classList.add('hidden'); this.nextElementSibling.classList.remove('hidden');" />
                    <div id="profilePhotoFallback" class="profile-photo-fallback hidden flex items-center justify-center rounded-full bg-slate-50 text-slate-500">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M20 21a8 8 0 0 0-16 0" />
                            <circle cx="12" cy="8" r="4" />
                        </svg>
                    </div>
                    <span class="profile-photo-crop-border" aria-hidden="true"></span>
                </div>
                <div class="profile-photo-frame-label"><spring:message code="profile.photo.frameLabel" text="Rounded profile crop area" /></div>
                <div id="profilePhotoEditor" class="profile-photo-editor hidden rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-left">
                    <label for="profilePhotoZoom" class="block text-xs font-bold uppercase tracking-[0.12em] text-slate-500">
                        <spring:message code="profile.photo.zoom" text="Zoom" />
                    </label>
                    <input id="profilePhotoZoom" type="range" min="1" max="3" step="0.01" value="1" class="mt-2" />
                    <button id="profilePhotoCenter" type="button" class="app-btn btn-neutral mt-2 w-full">
                        <spring:message code="profile.photo.center" text="Center Photo" />
                    </button>
                </div>
            </div>

            <form id="profilePhotoForm" action="/profile/image" method="post" enctype="multipart/form-data" class="space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label id="profilePhotoDropzone" class="profile-photo-dropzone block rounded-md p-5 text-center">
                    <span class="block text-sm font-semibold text-slate-800"><spring:message code="profile.photo.dropTitle" text="Drop your profile photo here" /></span>
                    <span class="mt-1 block text-sm text-slate-500"><spring:message code="profile.photo.dropHelp" text="or choose a PNG/JPEG image up to 2 MB. Drag and zoom it to fill the rounded crop area." /></span>
                    <input id="profileImageInput" type="file" name="profileImage" accept="image/png,image/jpeg" class="mt-4 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700" required />
                </label>
                <div class="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    <div class="font-semibold text-slate-800">${profileMember.fullName}</div>
                    <div class="mt-1">${profileMember.memberNo}</div>
                    <c:if test="${not empty profileMember.staffNo}">
                        <div class="mt-1">Staff Member Number: ${profileMember.staffNo}</div>
                    </c:if>
                    <c:if test="${not empty profileMember.email}">
                        <div class="mt-1">${profileMember.email}</div>
                    </c:if>
                </div>
                <div class="flex flex-wrap items-center justify-end gap-2">
                    <a href="javascript:history.back()" class="app-btn btn-neutral"><spring:message code="common.back" text="Back" /></a>
                    <c:if test="${hasProfileImage}">
                        <button type="submit"
                                class="app-btn btn-reject"
                                formaction="/profile/image/delete"
                                formmethod="post"
                                formnovalidate>
                            <spring:message code="profile.photo.remove" text="Remove Photo" />
                        </button>
                    </c:if>
                    <button type="submit" class="app-btn btn-primary"><spring:message code="profile.photo.save" text="Save Photo" /></button>
                </div>
            </form>
        </div>
    </div>
</section>

<script>
    (function () {
        var dropzone = document.getElementById('profilePhotoDropzone');
        var form = document.getElementById('profilePhotoForm');
        var input = document.getElementById('profileImageInput');
        var frame = document.getElementById('profilePhotoFrame');
        var preview = document.getElementById('profilePhotoPreview');
        var fallback = document.getElementById('profilePhotoFallback');
        var editor = document.getElementById('profilePhotoEditor');
        var zoomInput = document.getElementById('profilePhotoZoom');
        var centerButton = document.getElementById('profilePhotoCenter');
        var avatarImages = document.querySelectorAll('.profile-avatar-img');
        var selectedObjectUrl = null;
        var submittingCroppedPhoto = false;
        var avatarPreviewFrame = null;
        var crop = {
            fileName: '',
            mimeType: 'image/jpeg',
            naturalWidth: 0,
            naturalHeight: 0,
            zoom: 1,
            offsetX: 0,
            offsetY: 0,
            dragging: false,
            dragX: 0,
            dragY: 0,
            startOffsetX: 0,
            startOffsetY: 0
        };
        if (!dropzone || !form || !input || !frame || !preview) {
            return;
        }
        if (preview.complete && !preview.classList.contains('hidden') && preview.naturalWidth) {
            initializeCrop('profile-photo.jpg');
        } else {
            preview.addEventListener('load', function () {
                if (!crop.fileName && !preview.classList.contains('hidden')) {
                    initializeCrop('profile-photo.jpg');
                }
            }, { once: true });
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
        if (zoomInput) {
            zoomInput.addEventListener('input', function () {
                crop.zoom = Number(zoomInput.value) || 1;
                clampOffsets();
                renderCropPreview();
            });
        }
        if (centerButton) {
            centerButton.addEventListener('click', function () {
                crop.offsetX = 0;
                crop.offsetY = 0;
                renderCropPreview();
            });
        }
        frame.addEventListener('pointerdown', function (event) {
            if (!crop.naturalWidth || preview.classList.contains('hidden')) {
                return;
            }
            event.preventDefault();
            crop.dragging = true;
            crop.dragX = event.clientX;
            crop.dragY = event.clientY;
            crop.startOffsetX = crop.offsetX;
            crop.startOffsetY = crop.offsetY;
            preview.classList.add('is-dragging');
            if (typeof frame.setPointerCapture === 'function') {
                frame.setPointerCapture(event.pointerId);
            }
        });
        frame.addEventListener('pointermove', function (event) {
            if (!crop.dragging) {
                return;
            }
            crop.offsetX = crop.startOffsetX + event.clientX - crop.dragX;
            crop.offsetY = crop.startOffsetY + event.clientY - crop.dragY;
            clampOffsets();
            renderCropPreview();
        });
        ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (eventName) {
            frame.addEventListener(eventName, function () {
                crop.dragging = false;
                preview.classList.remove('is-dragging');
            });
        });
        form.addEventListener('submit', function (event) {
            if (submittingCroppedPhoto || !crop.fileName || !crop.naturalWidth) {
                return;
            }
            event.preventDefault();
            prepareCroppedUpload(function (file) {
                if (!file) {
                    form.submit();
                    return;
                }
                if (typeof DataTransfer !== 'function') {
                    form.submit();
                    return;
                }
                var transfer = new DataTransfer();
                transfer.items.add(file);
                input.files = transfer.files;
                submittingCroppedPhoto = true;
                if (typeof form.requestSubmit === 'function') {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
            });
        });

        function updatePreview(file) {
            if (!file || !file.type || file.type.indexOf('image/') !== 0) {
                return;
            }
            if (selectedObjectUrl) {
                URL.revokeObjectURL(selectedObjectUrl);
            }
            selectedObjectUrl = URL.createObjectURL(file);
            crop.fileName = file.name || 'profile-photo.jpg';
            crop.mimeType = file.type || 'image/jpeg';
            preview.onload = function () {
                initializeCrop(crop.fileName);
            };
            preview.src = selectedObjectUrl;
            preview.classList.remove('hidden');
            if (fallback) {
                fallback.classList.add('hidden');
            }
        }
        function initializeCrop(fileName) {
            crop.fileName = fileName || crop.fileName || 'profile-photo.jpg';
            crop.naturalWidth = preview.naturalWidth || 0;
            crop.naturalHeight = preview.naturalHeight || 0;
            crop.zoom = 1;
            crop.offsetX = 0;
            crop.offsetY = 0;
            input.required = false;
            if (zoomInput) {
                zoomInput.value = '1';
            }
            if (editor) {
                editor.classList.remove('hidden');
            }
            renderCropPreview();
        }
        function frameSize() {
            return {
                width: frame.clientWidth || 144,
                height: frame.clientHeight || 180
            };
        }
        function displaySize() {
            var size = frameSize();
            if (!crop.naturalWidth || !crop.naturalHeight) {
                return { width: size.width, height: size.height };
            }
            var baseScale = Math.max(size.width / crop.naturalWidth, size.height / crop.naturalHeight);
            return {
                width: crop.naturalWidth * baseScale * crop.zoom,
                height: crop.naturalHeight * baseScale * crop.zoom
            };
        }
        function clampOffsets() {
            var size = frameSize();
            var display = displaySize();
            var maxX = Math.max((display.width - size.width) / 2, 0);
            var maxY = Math.max((display.height - size.height) / 2, 0);
            crop.offsetX = Math.min(maxX, Math.max(-maxX, crop.offsetX));
            crop.offsetY = Math.min(maxY, Math.max(-maxY, crop.offsetY));
        }
        function renderCropPreview() {
            var size = frameSize();
            var display = displaySize();
            clampOffsets();
            preview.style.width = display.width + 'px';
            preview.style.height = display.height + 'px';
            preview.style.left = ((size.width - display.width) / 2 + crop.offsetX) + 'px';
            preview.style.top = ((size.height - display.height) / 2 + crop.offsetY) + 'px';
            queueAvatarPreview();
        }
        function drawCropToCanvas(canvas) {
            var size = frameSize();
            var display = displaySize();
            var scale = canvas.width / size.width;
            var left = (size.width - display.width) / 2 + crop.offsetX;
            var top = (size.height - display.height) / 2 + crop.offsetY;
            var context = canvas.getContext('2d');
            if (!context) {
                return false;
            }
            context.fillStyle = '#ffffff';
            context.fillRect(0, 0, canvas.width, canvas.height);
            context.drawImage(preview, left * scale, top * scale, display.width * scale, display.height * scale);
            return true;
        }
        function queueAvatarPreview() {
            if (!avatarImages.length || !crop.naturalWidth || avatarPreviewFrame) {
                return;
            }
            avatarPreviewFrame = window.requestAnimationFrame(function () {
                avatarPreviewFrame = null;
                var canvas = document.createElement('canvas');
                canvas.width = 96;
                canvas.height = 96;
                try {
                    if (!drawCropToCanvas(canvas)) {
                        return;
                    }
                    var dataUrl = canvas.toDataURL('image/jpeg', 0.9);
                    avatarImages.forEach(function (image) {
                        image.src = dataUrl;
                        image.classList.remove('hidden');
                        if (image.nextElementSibling) {
                            image.nextElementSibling.classList.add('hidden');
                        }
                    });
                } catch (error) {
                    // Keep the server-rendered avatar if the browser blocks canvas reads.
                }
            });
        }
        function prepareCroppedUpload(done) {
            var outputWidth = 600;
            var outputHeight = 600;
            var canvas = document.createElement('canvas');
            canvas.width = outputWidth;
            canvas.height = outputHeight;
            try {
                if (!drawCropToCanvas(canvas)) {
                    done(null);
                    return;
                }
            } catch (error) {
                done(null);
                return;
            }
            canvas.toBlob(function (blob) {
                if (!blob) {
                    done(null);
                    return;
                }
                var safeName = crop.fileName.replace(/\.[^.]+$/, '') || 'profile-photo';
                done(new File([blob], safeName + '-cropped.jpg', { type: 'image/jpeg' }));
            }, 'image/jpeg', 0.9);
        }
    })();
</script>

<%@ include file="fragments/footer.jspf" %>
