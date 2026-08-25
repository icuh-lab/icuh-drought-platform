document.addEventListener('DOMContentLoaded', function() {
    // URL에서 게시글 ID 추출
    const articleId = extractArticleIdFromUrl();
    
    if (!articleId) {
        showError('게시글 ID를 찾을 수 없습니다.');
        return;
    }
    
    // 게시글 상세 정보 가져오기
    fetchArticleDetail(articleId);
    
    // 뒤로 가기 버튼 이벤트 리스너
    const backButton = document.getElementById('backButton');
    if (backButton) {
        backButton.addEventListener('click', function() {
            window.history.back();
        });
    }
    
    // 승인 버튼 이벤트 리스너
    const approveButton = document.getElementById('approveButton');
    if (approveButton) {
        approveButton.addEventListener('click', function() {
            approveArticle(articleId);
        });
    }
    
    // 반려 버튼 이벤트 리스너
    const rejectButton = document.getElementById('rejectButton');
    const rejectModal = document.getElementById('rejectModal');
    const cancelReject = document.getElementById('cancelReject');
    const confirmReject = document.getElementById('confirmReject');
    
    if (rejectButton) {
        rejectButton.addEventListener('click', function() {
            showRejectModal();
        });
    }
    
    if (cancelReject) {
        cancelReject.addEventListener('click', function() {
            hideRejectModal();
        });
    }
    
    if (confirmReject) {
        confirmReject.addEventListener('click', function() {
            const reason = document.getElementById('rejectReason').value.trim();
            if (!reason) {
                alert('반려 사유를 입력해주세요.');
                return;
            }
            rejectArticle(articleId, reason);
            hideRejectModal();
        });
    }
});

// URL에서 게시글 ID 추출
function extractArticleIdFromUrl() {
    const pathParts = window.location.pathname.split('/');
    return pathParts[pathParts.length - 1];
}

// 게시글 상세 정보 가져오기
async function fetchArticleDetail(articleId) {
    try {
        const response = await fetch(`/api/v1/admin/articles/${articleId}`);
        
        if (!response.ok) {
            throw new Error('서버 응답 오류: ' + response.status);
        }
        
        const article = await response.json();
        displayArticleDetail(article);
        
        // 등록 대기 중인 경우 승인/반려 버튼 표시
        if (article.status === 'PENDING') {
            showActionButtons();
            document.getElementById('breadcrumbStatus').textContent = '등록 대기 중';
        } else {
            document.getElementById('breadcrumbStatus').textContent = '등록 완료됨';
        }
    } catch (error) {
        console.error('게시글 상세 정보를 가져오는 중 오류 발생:', error);
        showError('게시글 정보를 불러오는 중 오류가 발생했습니다.');
    }
}

// 게시글 상세 정보 표시
function displayArticleDetail(article) {
    // 로딩 인디케이터 숨기기
    document.getElementById('loadingIndicator').classList.add('hidden');
    
    // 게시글 내용 표시
    document.getElementById('articleContent').classList.remove('hidden');
    
    // 기본 정보 설정
    document.getElementById('articleTitle').textContent = article.title;
    document.getElementById('articleAuthor').textContent = article.author || '-';
    document.getElementById('articleDate').textContent = formatDate(article.createdAt);
    document.getElementById('articleOrganization').textContent = article.authorOrganization || '-';
    document.getElementById('articleDepartment').textContent = article.department || '-';
    document.getElementById('articleDocumentType').textContent = article.classification.name || '-';
    document.getElementById('articleSubjectDomain').textContent = article.serviceType.name || '-';
    
    // 요약 및 본문 설정
    document.getElementById('articleSummary').textContent = article.summary || '요약 정보가 없습니다.';
    document.getElementById('articleContentText').innerHTML = article.description || '내용이 없습니다.';
    
    // 상태 표시
    const statusElement = document.getElementById('articleStatus');
    const statusClass = getStatusClass(article.status);
    const statusText = getStatusDisplayText(article.status);
    statusElement.className = `px-3 py-1 text-xs font-semibold rounded-full ${statusClass}`;
    statusElement.textContent = statusText;
    
    // 첨부파일 설정
    const attachmentList = document.getElementById('attachmentList');
    const attachmentSection = document.getElementById('attachmentSection');
    
    if (article.files && article.files.length > 0) {
        attachmentList.innerHTML = '';
        article.files.forEach(file => {
            const li = document.createElement('li');
            li.className = 'mb-2 last:mb-0';
            
            const a = document.createElement('a');
            a.href = file.url;
            a.className = 'text-blue-600 hover:underline flex items-center';
            a.target = '_blank';
            
            a.innerHTML = `
                <svg class="w-4 h-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M8 4a3 3 0 00-3 3v4a5 5 0 0010 0V7a1 1 0 112 0v4a7 7 0 11-14 0V7a5 5 0 0110 0v4a3 3 0 11-6 0V7a1 1 0 012 0v4a1 1 0 102 0V7a3 3 0 00-3-3z" clip-rule="evenodd"></path>
                </svg>
                ${file.originalFilename || '첨부파일'}
            `;

            a.href = `http://infradna.io.kr:8081${file.downloadUrl}`;

            li.appendChild(a);
            attachmentList.appendChild(li);
        });
    } else {
        attachmentList.innerHTML = '<li class="text-gray-500">첨부파일이 없습니다.</li>';
    }
}

// 오류 메시지 표시
function showError(message) {
    document.getElementById('loadingIndicator').classList.add('hidden');
    
    const errorMessage = document.getElementById('errorMessage');
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
}

// 상태에 따른 표시 텍스트 반환
function getStatusDisplayText(status) {
    switch(status) {
        case 'APPROVED':
            return '승인완료';
        case 'REJECTED':
            return '반려됨';
        case 'PENDING':
            return '대기중';
        default:
            return status || '대기중';
    }
}

// 상태에 따른 클래스 반환
function getStatusClass(status) {
    switch(status) {
        case 'APPROVED':
            return 'bg-green-100 text-green-800';
        case 'REJECTED':
            return 'bg-red-100 text-red-800';
        case 'PENDING':
        default:
            return 'bg-yellow-100 text-yellow-800';
    }
}

// 날짜 포맷팅
function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// 승인/반려 버튼 표시
function showActionButtons() {
    const actionButtons = document.getElementById('actionButtons');
    if (actionButtons) {
        actionButtons.classList.remove('hidden');
    }
}

// 게시글 승인
async function approveArticle(articleId) {
    try {
        const response = await fetch(`/api/v2/articles/${articleId}`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error('서버 응답 오류: ' + response.status);
        }
        
        alert('게시글이 승인되었습니다.');
        // 페이지 새로고침하여 상태 업데이트
        window.location.reload();
    } catch (error) {
        console.error('게시글 승인 중 오류 발생:', error);
        alert('게시글 승인 중 오류가 발생했습니다. 나중에 다시 시도해주세요.');
    }
}

// 게시글 반려
async function rejectArticle(articleId, reason) {
    try {
        const response = await fetch(`/api/v2/articles/${articleId}/reject`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ reason: reason })
        });
        
        if (!response.ok) {
            throw new Error('서버 응답 오류: ' + response.status);
        }
        
        alert('게시글이 반려되었습니다.');
        // 페이지 새로고침하여 상태 업데이트
        window.location.reload();
    } catch (error) {
        console.error('게시글 반려 중 오류 발생:', error);
        alert('게시글 반려 중 오류가 발생했습니다. 나중에 다시 시도해주세요.');
    }
}

// 반려 모달 표시
function showRejectModal() {
    const rejectModal = document.getElementById('rejectModal');
    if (rejectModal) {
        rejectModal.classList.remove('hidden');
    }
}

// 반려 모달 숨기기
function hideRejectModal() {
    const rejectModal = document.getElementById('rejectModal');
    if (rejectModal) {
        rejectModal.classList.add('hidden');
        document.getElementById('rejectReason').value = '';
    }
}