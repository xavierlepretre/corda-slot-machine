(function () {

    function enterLeaderboard(nickname, fnSuccess, fnError) {
        window.ioServer.ajaxRequest(
            { name: window.accountName, nickname: nickname },
            "POST", "/enterLeaderboard",
            fnSuccess, fnError);
    };

    function loadLeaderboard() {
        getLeaderboard(displayLeaderboard, window.alert);
    };
    window.loadLeaderboard = loadLeaderboard;

    function getLeaderboard(fnSuccess, fnError) {
         window.ioServer.ajaxRequest(
         { name: window.accountName },
         "GET", "/leaderboard",
         fnSuccess, fnError);
    };

    function displayLeaderboard(receivedLeaderboard) {
        const leaderboardContent = $("#leaderboard_content").empty();
        receivedLeaderboard.forEach((entry, index) => {
            const newRow = $($("#leaderboard_entry").html());
            newRow.find(".index").text(index + 1);
            newRow.find(".when").text(entry.creationDate);
            newRow.find(".who").text(entry.nickname);
            newRow.find(".score").text(entry.total);
            newRow.find(".linearId").text(entry.linearId);
            if (entry.me) newRow.addClass("is_me");
            leaderboardContent.append(newRow);
        });
    };

    function leaveLeaderboard(fnSuccess, fnError) {
         window.ioServer.ajaxRequest({ name: window.accountName }, "POST", "/leaveLeaderboard", fnSuccess, fnError);
    };

    // Bind events
    function setupButtons() {
        $("#enter_leaderboard").click(() => {
            const nickname = $("#entry_nickname").val();
            if (typeof nickname === "undefined") {
                window.alert("Enter a nickname");
                throw new Error("Enter a nickname");
            }
            enterLeaderboard(
                nickname,
                loadLeaderboard,
                window.alert);
        });
        $("#load_leaderboard").click(() => {
            getLeaderboard(displayLeaderboard, window.alert);
        });
        $("#leave_leaderboard").click(() => {
            if (confirm("Are you sure to erase all your information in all leaderboards?")) {
                leaveLeaderboard(
                    loadLeaderboard,
                    window.alert);
            }
        });
    };

    $(window).on("load", function () {
        setupButtons();
        loadLeaderboard();
    });

})();
