var adminCtl = angular.module('AdminCtl', ['ngResource']);

/**
 * Sets up the application.
 */
adminCtl.run( function($rootScope)
{
    $rootScope.NEW_ID = -1;
    $rootScope.STATE_NEW = 'new';
    $rootScope.STATE_EDIT = 'edit';

    $rootScope.isPresent = function(variable)
    {
        return variable !== null && typeof variable !== 'undefined';
    };
});

/**
 * Controller for the menu.
 */
adminCtl.controller("MenuCtl", function($rootScope, $location)
{
    $rootScope.isActive = function (viewLocation) {
        return viewLocation === $location.path();
    };
});

/**
 * Controller for credentials page
 */
adminCtl.controller('CredsCtl', function($scope, $rootScope, $http, $resource, $filter, $timeout, $interval)
{
    var UserResource = $resource('/api/users', {}, {
        getList:    { method: 'GET', isArray:true}
    });

    var reloadData = function() {
        UserResource.getList(function(response)
        {
            $scope.users = response;
        });
    };

    var promise = $interval(reloadData, 500);

    $scope.reload = function()
    {
        UserResource.getList(function(response)
        {
            $scope.users = response;
        });
    };

    $scope.submitBuildingUser = function()
    {
        if($scope.buildingUser['password'] !== $scope.buildingUser['passwordRepeat'])
        {
            alert("Password does not match the password confirmation");
            return;
        }

        if($scope.buildingUser['password'].length == 0)
        {
            alert("Nothing to update");
            return;
        }

        if($scope.buildingState === 'edit' || $scope.buildingState === 'new')
        {
            $http(
                {
                    url: '/api/users',
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    data: JSON.stringify({
                        username: $scope.buildingUser.username,
                        password: $scope.buildingUser.password,
                    })
                }
            ).success(function(data)
            {
                $('#modalUser').modal('hide');
                $scope.reload();
            }).error(function()
            {
                alert("An unknown error occurred. Contact your administrator.");
            });
        }
        else
            alert("Exception, illegal state '"+$scope.buildingState+"'");
    };

    $scope.deleteBuildingUser = function()
    {
        $http(
            {
                url: '/api/users/'+$scope.buildingUser['username'],
                method: 'DELETE'
            }
        ).success(function(data)
        {
            $('#modalUser').modal('hide');
            $scope.reload();
        }).error(function()
        {
            alert("An unknown error occurred. Contact your administrator.");
        });
    };

    $scope.getPlaceholderText = function(forInputItem)
    {
        if(forInputItem === 'username')
            return "Choose a username";

        if(forInputItem === 'password' && $scope.buildingState == 'edit')
            return "Enter a new password only if you wish to change it";

        if(forInputItem === 'password' && $scope.buildingState == 'new')
            return "Choose a password";

        var passwordLength = $scope.buildingUser.password.length;
        if(forInputItem == 'passwordConfirm' && passwordLength > 0 && $scope.buildingState == 'edit')
            return "Confirm the new password";

        if(forInputItem == 'passwordConfirm' && passwordLength > 0
            && $scope.buildingState == 'new')
            return "Confirm the chosen password";

        return "";
    };
    
    $scope.getDisabled = function(forInputItem)
    {
        return forInputItem === 'username' && $scope.buildingState === 'edit';
    };

    $scope.resetBuildingUser = function()
    {
        $scope.createBuildingUser('', 'new');
    };

    $scope.loadBuildingUser = function(username) {
        $http.get('/api/users/'+username, {}, { isArray:true })
            .success( function(response) {
                console.log(response);
                if($rootScope.isPresent(response.username)) {
                    var user = response;
                    $scope.createBuildingUser(user.username, 'edit');
                }
            }
        );
    };

    /**
     *  @param username  string  username to create buildingUser for
     *  @param roles     array   list or assigned roles
     *  @param state     string  'new' or 'edit'
     */
    $scope.createBuildingUser = function(username, state)
    {
        if(state !== 'new' && state !== 'edit') {
            console.error("Illegal state on buildingUser: "+state);
        }

        $scope.buildingState = state;
        $scope.buildingUser = {username: username, password: '', passwordRepeat: ''};
    };

    $scope.reload();
    $scope.resetBuildingUser();
});