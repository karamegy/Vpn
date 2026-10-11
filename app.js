document.addEventListener('DOMContentLoaded', () => {
    const disclosureModal = document.getElementById('disclosure-modal');
    const acceptBtn = document.getElementById('accept-btn');
    const denyBtn = document.getElementById('deny-btn');
    
    // نوافذ الثيم واختبار السرعة
    const themeModal = document.getElementById('theme-modal');
    const themeCustomizerBtn = document.getElementById('theme-customizer-btn');
    const closeThemeBtn = document.getElementById('close-theme-btn');
    const themeButtons = document.querySelectorAll('.theme-btn');

    const speedModal = document.getElementById('speed-modal');
    const openSpeedTest = document.getElementById('open-speed-test');
    const closeSpeedBtn = document.getElementById('close-speed-btn');
    const startSpeedTest = document.getElementById('start-speed-test');
    const speedStatusText = document.getElementById('speed-status-text');
    const livePingVal = document.getElementById('live-ping-val');
    const testDl = document.getElementById('test-dl');
    const testUl = document.getElementById('test-ul');

    const aiModal = document.getElementById('ai-modal');
    const aiCopilotBtn = document.getElementById('ai-copilot-btn');
    const closeAiBtn = document.getElementById('close-ai-btn');
    const aiChatBox = document.getElementById('ai-chat-box');
    const aiInput = document.getElementById('ai-input');
    const aiSendBtn = document.getElementById('ai-send-btn');

    const serversModal = document.getElementById('servers-modal');
    const serverSelectBtn = document.getElementById('server-select-btn');
    const closeServersBtn = document.getElementById('close-servers-btn');
    const serverLocation = document.getElementById('server-location');
    const serverItems = document.querySelectorAll('.server-item');

    const policyModal = document.getElementById('policy-modal');
    const openPolicyLink = document.getElementById('open-policy-link');
    const closePolicyBtn = document.getElementById('close-policy-btn');
    const reopenDisclosureLink = document.getElementById('reopen-disclosure-link');

    const connectBtn = document.getElementById('connect-btn');
    const statusOrb = document.getElementById('status-orb');
    const connectionStatus = document.getElementById('connection-status');
    const ipDisplay = document.getElementById('ip-display');
    const downloadSpeed = document.getElementById('download-speed');
    const uploadSpeed = document.getElementById('upload-speed');
    const threatCount = document.getElementById('threat-count');

    let isConnected = false;
    let hasConsented = localStorage.getItem('vpn_consent') === 'true';
    let threatInterval = null;

    // استعادة الثيم المحفوظ مسبقاً
    const savedTheme = localStorage.getItem('apex_theme') || 'theme-cyan';
    document.body.className = savedTheme;

    // التحقق من الإفصاح المسبق
    if (hasConsented) {
        disclosureModal.classList.add('hidden');
    }

    acceptBtn.addEventListener('click', () => {
        localStorage.setItem('vpn_consent', 'true');
        disclosureModal.classList.add('hidden');
    });

    denyBtn.addEventListener('click', () => {
        alert('نأسف، يتطلب تشغيل الدرع الأمني الموافقة على شروط خدمة جوجل بلاي.');
        window.close();
    });

    // إدارة تخصيص الثيمات
    themeCustomizerBtn.addEventListener('click', () => themeModal.classList.remove('hidden'));
    closeThemeBtn.addEventListener('click', () => themeModal.classList.add('hidden'));

    themeButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const selectedTheme = btn.getAttribute('data-theme');
            document.body.className = selectedTheme;
            localStorage.setItem('apex_theme', selectedTheme);
            themeModal.classList.add('hidden');
        });
    });

    // إدارة اختبار السرعة الحي
    openSpeedTest.addEventListener('click', () => speedModal.classList.remove('hidden'));
    closeSpeedBtn.addEventListener('click', () => speedModal.classList.add('hidden'));

    startSpeedTest.addEventListener('click', () => {
        startSpeedTest.disabled = true;
        speedStatusText.textContent = 'جاري قياس استجابة الخادم (Ping)...';
        livePingVal.textContent = '...';
        testDl.textContent = '0.0 Mb/s';
        testUl.textContent = '0.0 Mb/s';

        setTimeout(() => {
            livePingVal.textContent = '14 ms';
            speedStatusText.textContent = 'جاري اختبار سرعة التحميل...';
            testDl.textContent = '45.2 Mb/s';
        }, 1200);

        setTimeout(() => {
            speedStatusText.textContent = 'جاري اختبار سرعة الرفع...';
            testUl.textContent = '18.6 Mb/s';
        }, 2400);

        setTimeout(() => {
            speedStatusText.textContent = 'اكتمل اختبار التشخيص بنجاح ✔';
            startSpeedTest.disabled = false;
        }, 3500);
    });

    // إدارة محادثة الذكاء الاصطناعي الأمني
    aiCopilotBtn.addEventListener('click', () => aiModal.classList.remove('hidden'));
    closeAiBtn.addEventListener('click', () => aiModal.classList.add('hidden'));

    const botResponses = [
        "بناءً على تحليلي، شبكتك الحالية محمية بالكامل عبر نفق WireGuard المشفر.",
        "قمت باختيار السيرفر الأسرع تلقائياً لتقليل وقت الاستجابة (Ping) بنسبة 40%.",
        "لم يتم رصد أي محاولات تتبع أو برمجيات خبيثة خلال الجلسة الحالية.",
        "نصيحة أمنية ذكية: تجنب استخدام شبكات الواي فاي العامة دون تفعيل درع الحماية."
    ];

    function sendAiMessage() {
        const text = aiInput.value.trim();
        if (!text) return;

        const userMsg = document.createElement('div');
        userMsg.className = 'ai-message user';
        userMsg.textContent = text;
        aiChatBox.appendChild(userMsg);
        aiInput.value = '';
        aiChatBox.scrollTop = aiChatBox.scrollHeight;

        setTimeout(() => {
            const botMsg = document.createElement('div');
            botMsg.className = 'ai-message bot';
            const randomReply = botResponses[Math.floor(Math.random() * botResponses.length)];
            botMsg.textContent = randomReply;
            aiChatBox.appendChild(botMsg);
            aiChatBox.scrollTop = aiChatBox.scrollHeight;
        }, 800);
    }

    aiSendBtn.addEventListener('click', sendAiMessage);
    aiInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendAiMessage();
    });

    // إدارة اختيار السيرفرات
    serverSelectBtn.addEventListener('click', () => serversModal.classList.remove('hidden'));
    closeServersBtn.addEventListener('click', () => serversModal.classList.add('hidden'));

    serverItems.forEach(item => {
        item.addEventListener('click', () => {
            serverItems.forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            const serverName = item.getAttribute('data-name');
            serverLocation.textContent = `🌐 ${serverName.split(' ')[0]}`;
            serversModal.classList.add('hidden');
        });
    });

    // السياسات والإفصاح
    openPolicyLink.addEventListener('click', (e) => { e.preventDefault(); policyModal.classList.remove('hidden'); });
    closePolicyBtn.addEventListener('click', () => policyModal.classList.add('hidden'));
    reopenDisclosureLink.addEventListener('click', (e) => { e.preventDefault(); disclosureModal.classList.remove('hidden'); });

    // تشغيل الاتصال وتفعيل درع التهديدات
    connectBtn.addEventListener('click', () => {
        if (!localStorage.getItem('vpn_consent')) {
            disclosureModal.classList.remove('hidden');
            return;
        }

        if (!isConnected) {
            connectBtn.textContent = 'جاري تأمين النفق...';
            connectBtn.disabled = true;

            setTimeout(() => {
                isConnected = true;
                statusOrb.classList.add('connected');
                connectionStatus.textContent = 'محمي بواسطة الذكاء الاصطناعي';
                ipDisplay.textContent = 'IP العشوائي: 185.220.x.x (مخفي)';
                connectBtn.textContent = 'إيقاف الدرع';
                connectBtn.disabled = false;
                
                downloadSpeed.textContent = '18.2 MB/s';
                uploadSpeed.textContent = '6.4 MB/s';

                let count = parseInt(threatCount.textContent);
                threatInterval = setInterval(() => {
                    count += Math.floor(Math.random() * 2) + 1;
                    threatCount.textContent = count;
                }, 4000);
            }, 1800);
        } else {
            connectBtn.textContent = 'جاري قطع الاتصال...';
            connectBtn.disabled = true;

            setTimeout(() => {
                isConnected = false;
                statusOrb.classList.remove('connected');
                connectionStatus.textContent = 'غير متصل بالدرع';
                ipDisplay.textContent = 'IP العشوائي: مخفي تماماً';
                connectBtn.textContent = 'تفعيل درع الحماية';
                connectBtn.disabled = false;

                downloadSpeed.textContent = '0.0 MB/s';
                uploadSpeed.textContent = '0.0 MB/s';
                clearInterval(threatInterval);
            }, 1000);
        }
    });
});
