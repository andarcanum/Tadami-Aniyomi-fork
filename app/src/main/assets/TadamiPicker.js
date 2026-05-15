(function() {
    if (window.TadamiPickerInitialized) return;
    window.TadamiPickerInitialized = true;

    const overlay = document.createElement('div');
    overlay.style.position = 'absolute';
    overlay.style.border = '2px solid #00FF00';
    overlay.style.backgroundColor = 'rgba(0, 255, 0, 0.2)';
    overlay.style.pointerEvents = 'none';
    overlay.style.zIndex = '999999';
    overlay.style.transition = 'all 0.1s';
    document.body.appendChild(overlay);

    let currentTarget = null;
    let pickerEnabled = false;

    function getCssSelector(el) {
        if (!(el instanceof Element)) return;
        var path = [];
        while (el.nodeType === Node.ELEMENT_NODE) {
            var selector = el.nodeName.toLowerCase();
            if (el.id) {
                selector += '#' + el.id;
                path.unshift(selector);
                break;
            } else {
                var sib = el, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector) nth++;
                }
                if (nth != 1) selector += ":nth-of-type("+nth+")";
                
                // Add class names for better specificity if no ID
                if (el.className && typeof el.className === 'string') {
                    const classes = el.className.trim().split(/\s+/).filter(c => c && !c.includes('hover') && !c.includes('active'));
                    if (classes.length > 0) {
                        selector += '.' + classes.join('.');
                    }
                }
            }
            path.unshift(selector);
            el = el.parentNode;
        }
        return path.join(" > ");
    }

    document.addEventListener('mouseover', function(e) {
        if (!pickerEnabled) return;
        currentTarget = e.target;
        const rect = currentTarget.getBoundingClientRect();
        overlay.style.top = (rect.top + window.scrollY) + 'px';
        overlay.style.left = (rect.left + window.scrollX) + 'px';
        overlay.style.width = rect.width + 'px';
        overlay.style.height = rect.height + 'px';
    });

    document.addEventListener('click', function(e) {
        if (!pickerEnabled) return;
        e.preventDefault();
        e.stopPropagation();
        
        if (currentTarget) {
            const selector = getCssSelector(currentTarget);
            const text = currentTarget.innerText || currentTarget.value || '';
            
            // Visual feedback
            overlay.style.backgroundColor = 'rgba(255, 0, 0, 0.5)';
            setTimeout(() => { overlay.style.backgroundColor = 'rgba(0, 255, 0, 0.2)'; }, 300);
            
            if (window.TadamiBridge) {
                window.TadamiBridge.onElementSelected(selector, text);
            }
        }
    }, true);

    window.TadamiPicker = {
        enable: function() {
            pickerEnabled = true;
            overlay.style.display = 'block';
        },
        disable: function() {
            pickerEnabled = false;
            overlay.style.display = 'none';
        },
        setInteractionMode: function(interact) {
            pickerEnabled = !interact;
            overlay.style.display = interact ? 'none' : 'block';
        }
    };
})();
