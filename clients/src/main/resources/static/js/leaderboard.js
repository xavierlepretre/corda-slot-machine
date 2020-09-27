(function () {

    function enterLeaderboard(nickname, fnSuccess, fnError) {
        window.ioServer.ajaxRequest(
            { name: window.accountName, nickname: nickname },
            "POST", "/enterLeaderboard",
            fnSuccess, fnError);
    }

    function getLeaderboard(fnSuccess, fnError) {
         window.ioServer.ajaxRequest({}, "GET", "/leaderboard", fnSuccess, fnError);
    };

    function displayLeaderboard(receivedLeaderboard) {
        const leaderboardContent = $("#leaderboard_content").empty();
        receivedLeaderboard.forEach((entry) => {
            const newRow = $($("#leaderboard_entry").html());
            newRow.find(".when").text(entry.creationDate);
            newRow.find(".who").text(entry.nickname);
            newRow.find(".score").text(entry.total);
            newRow.find(".linearId").text(entry.linearId);
            leaderboardContent.append(newRow);
        });
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
                () => getLeaderboard(displayLeaderboard, window.alert),
                window.alert);
        });
        $("#load_leaderboard").click(() => {
            getLeaderboard(displayLeaderboard, window.alert);
        });
    };

    $(window).on("load", function () {
        setupButtons();
    });

})();
