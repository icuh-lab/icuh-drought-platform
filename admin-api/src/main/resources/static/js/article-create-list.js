document.addEventListener('DOMContentLoaded', function() {
    // 초기에는 등록 대기 중인 게시글 목록 가져오기
    fetchPendingArticles();
    
    // 탭 전환 기능 구현
    const pendingTab = document.getElementById('pendingTab');
    const approvedTab = document.getElementById('approvedTab');
    
    if (pendingTab) {
        pendingTab.addEventListener('click', function() {
            setActiveTab('pending');
            fetchPendingArticles();
        });
    }
    
    if (approvedTab) {
        approvedTab.addEventListener('click', function() {
            setActiveTab('approved');
            fetchApprovedArticles();
        });
    }
    
    // 검색 기능 구현
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', function() {
            filterArticles(this.value);
        });
    }
    
    // 디버깅용 콘솔 로그
    console.log('DOM 로드 완료: 이벤트 리스너 설정됨');
    if (pendingTab) console.log('등록 대기 중 탭 찾음');
    if (approvedTab) console.log('등록 완료됨 탭 찾음');
});

// 활성 탭 설정
function setActiveTab(tabType) {
    const pendingTab = document.getElementById('pendingTab');
    const approvedTab = document.getElementById('approvedTab');
    
    if (!pendingTab || !approvedTab) {
        console.error('탭 요소를 찾을 수 없습니다.');
        return;
    }
    
    if (tabType === 'pending') {
        pendingTab.className = 'px-6 py-3 border-b-2 border-blue-500 text-blue-500 font-medium';
        approvedTab.className = 'px-6 py-3 text-gray-500 hover:text-gray-700';
        console.log('등록 대기 중 탭 활성화');
    } else if (tabType === 'approved') {
        pendingTab.className = 'px-6 py-3 text-gray-500 hover:text-gray-700';
        approvedTab.className = 'px-6 py-3 border-b-2 border-blue-500 text-blue-500 font-medium';
        console.log('등록 완료됨 탭 활성화');
    }
}

// 등록 대기 중인 게시글 목록 불러오기
async function fetchPendingArticles() {
    try {
        console.log('등록 대기 중 게시글 요청 시작');
        const response = await fetch('/api/v2/articles?status=PENDING');
        
        if (!response.ok) {
            throw new Error('서버 응답 오류: ' + response.status);
        }
        
        const articles = await response.json();
        console.log('등록 대기 중 게시글 수신 완료:', articles.length);
        displayArticles(articles);
        updateTotalCount(articles.length, '등록 대기 중');
        setupPagination(articles);
    } catch (error) {
        console.error('게시글 데이터를 가져오는 중 오류 발생:', error);
        handleFetchError();
    }
}

// 등록 완료된 게시글 목록 불러오기
async function fetchApprovedArticles() {
    try {
        console.log('등록 완료됨 게시글 요청 시작');
        const response = await fetch('/api/v2/articles?status=APPROVED');
        
        if (!response.ok) {
            throw new Error('서버 응답 오류: ' + response.status);
        }
        
        const articles = await response.json();
        console.log('등록 완료됨 게시글 수신 완료:', articles.length);
        displayArticles(articles);
        updateTotalCount(articles.length, '등록 완료됨');
        setupPagination(articles);
    } catch (error) {
        console.error('게시글 데이터를 가져오는 중 오류 발생:', error);
        handleFetchError();
    }
}

// 오류 처리 함수
function handleFetchError() {
    const tableBody = document.getElementById('productTable');
    if (tableBody) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="6" class="px-6 py-4 text-center text-red-500">
                    데이터를 불러오는 중 오류가 발생했습니다. 나중에 다시 시도해주세요.
                </td>
            </tr>
        `;
    }
    
    // 페이지네이션과 총 개수 초기화
    const paginationContainer = document.getElementById('paginationContainer');
    if (paginationContainer) {
        paginationContainer.innerHTML = '';
    }
    
    updateTotalCount(0);
}

// 게시글 목록 화면에 표시
function displayArticles(articles, page = 1, itemsPerPage = 10) {
    const tableBody = document.getElementById('productTable');
    if (!tableBody) return;
    
    // 페이지네이션 처리
    const startIndex = (page - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    const paginatedArticles = articles.slice(startIndex, endIndex);
    
    // 테이블 내용 비우기
    tableBody.innerHTML = '';
    
    // 데이터가 없는 경우
    if (paginatedArticles.length === 0) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="6" class="px-6 py-4 text-center text-gray-500">
                    표시할 게시글이 없습니다.
                </td>
            </tr>
        `;
        return;
    }
    
    // 데이터 채우기
    paginatedArticles.forEach(article => {
        const row = document.createElement('tr');
        row.className = 'hover:bg-gray-50';
        row.innerHTML = `
            <td class="px-6 py-4 whitespace-nowrap">
                <a href="/article/detail/${article.id}" class="text-blue-600 hover:underline">
                    ${article.title}
                </a>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">${article.authorOrganization || '-'}</td>
            <td class="px-6 py-4 whitespace-nowrap">${article.department || '-'}</td>
            <td class="px-6 py-4 whitespace-nowrap">${article.author || '-'}</td>
            <td class="px-6 py-4 whitespace-nowrap">
                <span class="px-2 py-1 text-xs font-semibold rounded-full ${getStatusClass(article.status)}">
                    ${getStatusDisplayText(article.status)}
                </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">${formatDate(article.createdAt)}</td>
        `;
        tableBody.appendChild(row);
    });
}

// 상태에 따른 표시 텍스트 반환
function getStatusDisplayText(status) {
    switch(status) {
        case 'APPROVED':
            return '승인완료';
        case 'REJECTED':
            return '거부됨';
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
        day: '2-digit'
    });
}

// 총 게시글 수 업데이트
function updateTotalCount(count, tabName = '') {
    const totalCountElement = document.getElementById('totalCount');
    if (totalCountElement) {
        totalCountElement.textContent = `총 ${count}개의 게시글${tabName ? ' (' + tabName + ')' : ''}`;
    }
}

// 페이지네이션 설정
function setupPagination(articles, itemsPerPage = 10) {
    const paginationContainer = document.getElementById('paginationContainer');
    if (!paginationContainer) return;
    
    const totalPages = Math.ceil(articles.length / itemsPerPage);
    paginationContainer.innerHTML = '';
    
    // 페이지가 1페이지면 페이지네이션 표시하지 않음
    if (totalPages <= 1) return;
    
    // 이전 페이지 버튼
    const prevButton = document.createElement('button');
    prevButton.innerHTML = '&laquo;';
    prevButton.className = 'px-3 py-1 rounded-md bg-gray-200 text-gray-700 mx-1';
    prevButton.addEventListener('click', () => {
        const currentPage = parseInt(paginationContainer.getAttribute('data-current-page') || '1');
        if (currentPage > 1) {
            changePage(currentPage - 1, articles, itemsPerPage);
        }
    });
    paginationContainer.appendChild(prevButton);
    
    // 페이지 번호 버튼
    for (let i = 1; i <= totalPages; i++) {
        const pageButton = document.createElement('button');
        pageButton.textContent = i;
        pageButton.className = i === 1 
            ? 'px-3 py-1 rounded-md bg-blue-500 text-white mx-1' 
            : 'px-3 py-1 rounded-md bg-gray-200 text-gray-700 mx-1';
        pageButton.addEventListener('click', () => {
            changePage(i, articles, itemsPerPage);
        });
        paginationContainer.appendChild(pageButton);
    }
    
    // 다음 페이지 버튼
    const nextButton = document.createElement('button');
    nextButton.innerHTML = '&raquo;';
    nextButton.className = 'px-3 py-1 rounded-md bg-gray-200 text-gray-700 mx-1';
    nextButton.addEventListener('click', () => {
        const currentPage = parseInt(paginationContainer.getAttribute('data-current-page') || '1');
        if (currentPage < totalPages) {
            changePage(currentPage + 1, articles, itemsPerPage);
        }
    });
    paginationContainer.appendChild(nextButton);
    
    // 현재 페이지 저장
    paginationContainer.setAttribute('data-current-page', '1');
    
    // 캐시된 데이터 저장
    paginationContainer.setAttribute('data-articles', JSON.stringify(articles));
}

// 페이지 변경
function changePage(page, articles, itemsPerPage) {
    const paginationContainer = document.getElementById('paginationContainer');
    if (!paginationContainer) return;
    
    // 현재 페이지 업데이트
    paginationContainer.setAttribute('data-current-page', page);
    
    // 페이지 버튼 스타일 업데이트
    const pageButtons = paginationContainer.querySelectorAll('button:not(:first-child):not(:last-child)');
    pageButtons.forEach((button, index) => {
        if (index + 1 === page) {
            button.className = 'px-3 py-1 rounded-md bg-blue-500 text-white mx-1';
        } else {
            button.className = 'px-3 py-1 rounded-md bg-gray-200 text-gray-700 mx-1';
        }
    });
    
    // 게시글 표시 업데이트
    displayArticles(articles, page, itemsPerPage);
}

// 게시글 검색 필터링
function filterArticles(searchTerm) {
    const paginationContainer = document.getElementById('paginationContainer');
    if (!paginationContainer) return;
    
    // 캐시된 데이터 가져오기
    const articlesData = paginationContainer.getAttribute('data-articles');
    if (!articlesData) return;
    
    const articles = JSON.parse(articlesData);
    searchTerm = searchTerm.toLowerCase();
    
    // 검색어로 필터링
    const filteredArticles = articles.filter(article => {
        return (
            (article.title && article.title.toLowerCase().includes(searchTerm)) ||
            (article.authorOrganization && article.authorOrganization.toLowerCase().includes(searchTerm)) ||
            (article.department && article.department.toLowerCase().includes(searchTerm)) ||
            (article.author && article.author.toLowerCase().includes(searchTerm))
        );
    });
    
    // 필터링된 결과 표시
    displayArticles(filteredArticles);
    updateTotalCount(filteredArticles.length, '검색 결과');
    
    // 페이지네이션 업데이트
    if (filteredArticles.length > 0) {
        setupPagination(filteredArticles);
    } else {
        const paginationContainer = document.getElementById('paginationContainer');
        if (paginationContainer) {
            paginationContainer.innerHTML = '';
        }
    }
}